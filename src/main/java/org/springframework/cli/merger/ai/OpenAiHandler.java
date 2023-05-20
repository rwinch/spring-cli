/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.springframework.cli.merger.ai;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import org.springframework.cli.SpringCliException;
import org.springframework.cli.runtime.engine.actions.InjectMavenDependency;
import org.springframework.cli.runtime.engine.actions.handlers.InjectMavenDependencyActionHandler;
import org.springframework.cli.runtime.engine.templating.HandlebarsTemplateEngine;
import org.springframework.cli.util.ClassNameExtractor;
import org.springframework.cli.util.IoUtils;
import org.springframework.cli.util.PropertyFileUtils;
import org.springframework.cli.util.RootPackageFinder;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OpenAiHandler implements AiHandler {

	public void add(String project, String path, TerminalMessage terminalMessage) {
		ProjectName projectName = deriveProjectName(project);
		Path projectPath = IoUtils.getProjectPath(path);
		Map<String, String> context = getContext(projectName, projectPath);
		String prompt = createPrompt(context);
		String response = generate(prompt);
		List<ProjectArtifact> projectArtifacts = createProjectArtifacts(response);
		processArtifacts(projectArtifacts, projectName, projectPath, terminalMessage);
	}

	@Override
	public String generate(String prompt) {
		// get api token in file ~/.openai
		Properties properties = PropertyFileUtils.getPropertyFile();
		String apiKey = properties.getProperty("OPEN_AI_API_KEY");
		OpenAiService openAiService = new OpenAiService(apiKey, Duration.of(5, ChronoUnit.MINUTES));

		String USER_MESSAGE = prompt;
		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
				.builder()
				.model("gpt-3.5-turbo")
				.temperature(0.8)
				.messages(
						List.of(new ChatMessage("user", USER_MESSAGE)))
				.build();

		StringBuilder builder = new StringBuilder();
		openAiService.createChatCompletion(chatCompletionRequest)
				.getChoices().forEach(choice -> {
					builder.append(choice.getMessage().getContent());
				});

		return builder.toString();
	}

	private ProjectName deriveProjectName(String project) {
		ProjectNameHeuristic projectNameHeuristic = new ProjectNameHeuristic();
		return projectNameHeuristic.deriveProjectName(project);
	}

	private Map<String, String> getContext(ProjectName projectName, Path path) {
		Map<String, String> context = new HashMap<>();
		context.put("build-tool", "maven");
		context.put("package-name", calculatePackage(projectName, path));
		context.put("spring-project-name", projectName.getProjectName());
		return context;
	}

	private String calculatePackage(ProjectName projectName, Path path) {
		Optional<String> rootPackage = RootPackageFinder.findRootPackage(path.toFile());
		if (rootPackage.isEmpty()) {
			throw new SpringCliException("Could not find root package from path " + path.toAbsolutePath());
		}
		return rootPackage.get() + ".ai." + projectName.getShortName();
	}

	List<ProjectArtifact> createProjectArtifacts(String response) {
		ProjectArtifactProcessor projectArtifactProcessor = new ProjectArtifactProcessor();
		List<ProjectArtifact> projectArtifacts = projectArtifactProcessor.process(response);
		return projectArtifacts;
	}

	private String createPrompt(Map<String, String> context) {
		ClassPathResource classPathResource = new ClassPathResource("/org/springframework/cli/merger/ai/openai-prompt.txt");
		HandlebarsTemplateEngine handlebarsTemplateEngine = new HandlebarsTemplateEngine();
		try {
			String rawPrompt = StreamUtils.copyToString(classPathResource.getInputStream(), UTF_8);
			return handlebarsTemplateEngine.process(rawPrompt, context);
		}
		catch (IOException e) {
			throw new SpringCliException("Could not read resource " + classPathResource);
		}
	}

	private void processArtifacts(List<ProjectArtifact> projectArtifacts, ProjectName projectName, Path projectPath, TerminalMessage terminalMessage) {
		for (ProjectArtifact projectArtifact : projectArtifacts) {
			try {
				ProjectArtifactType artifactType = projectArtifact.getArtifactType();
				switch (artifactType) {
					case SOURCE_CODE:
						writeSourceCode(projectArtifact, projectName, projectPath);
						break;
					case TEST_CODE:
						writeTestCode(projectArtifact, projectName, projectPath);
						break;
					case MAVEN_DEPENDENCIES:
						writeMavenDependencies(projectArtifact, projectName, projectPath, terminalMessage);
						break;
					case APPLICATION_PROPERTIES:
						break;
					case MAIN_CLASS:
						break;
					default:
						break;
				}
			}
			catch (IOException ex) {
				throw new SpringCliException("Could not write project artifact.", ex);
			}
		}
	}

	private void writeMavenDependencies(ProjectArtifact projectArtifact, ProjectName projectName, Path projectPath, TerminalMessage terminalMessage) {
		InjectMavenDependencyActionHandler injectMavenDependencyActionHandler =
				new InjectMavenDependencyActionHandler(projectPath, terminalMessage);
		InjectMavenDependency injectMavenDependency = new InjectMavenDependency(projectArtifact.getText());
		injectMavenDependencyActionHandler.execute(injectMavenDependency);
	}

	private void writeTestCode(ProjectArtifact projectArtifact, ProjectName projectName, Path projectPath) throws IOException {
		String packageName = calculatePackage(projectName, projectPath);
		String className = ClassNameExtractor.extractClassName(projectArtifact.getText());
		Path output = createTestFile(projectPath, packageName, className + ".java");
		Files.createDirectories(output.getParent());
		try (Writer writer = new BufferedWriter(new FileWriter(output.toFile()))) {
			writer.write(projectArtifact.getText());
		}
	}

	private void writeSourceCode(ProjectArtifact projectArtifact, ProjectName projectName, Path projectPath) throws IOException {
		String packageName = calculatePackage(projectName, projectPath);
		String className = ClassNameExtractor.extractClassName(projectArtifact.getText());
		Path output = createSourceFile(projectPath, packageName, className + ".java");
		Files.createDirectories(output.getParent());
		try (Writer writer = new BufferedWriter(new FileWriter(output.toFile()))) {
			writer.write(projectArtifact.getText());
		}
	}

	private Path createSourceFile(Path projectPath, String packageName, String fileName) throws IOException {
		Path sourceFile = resolveSourceFile(projectPath, packageName, fileName);
		createFile(sourceFile);
		return sourceFile;
	}

	private Path createTestFile(Path projectPath, String packageName, String fileName) throws IOException {
		Path sourceFile = resolveTestFile(projectPath, packageName, fileName);
		createFile(sourceFile);
		return sourceFile;
	}

	public Path resolveSourceFile(Path projectPath, String packageName, String fileName) {
		Path sourceDirectory = projectPath.resolve("src").resolve("main").resolve("java");
		return resolvePackage(sourceDirectory, packageName).resolve(fileName);
	}

	public Path resolveTestFile(Path projectPath, String packageName, String fileName) {
		Path sourceDirectory = projectPath.resolve("src").resolve("test").resolve("java");
		return resolvePackage(sourceDirectory, packageName).resolve(fileName);
	}

	private static Path resolvePackage(Path directory, String packageName) {
		return directory.resolve(packageName.replace('.', '/'));
	}

	private void createFile(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.createFile(file);
	}

}
