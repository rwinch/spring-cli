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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.jetbrains.annotations.NotNull;

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

	private final HandlebarsTemplateEngine handlebarsTemplateEngine = new HandlebarsTemplateEngine();

	public void add(String description, String path, TerminalMessage terminalMessage) {
		Path projectPath = getProjectPath(path);

		ProjectNameHeuristic projectNameHeuristic = new ProjectNameHeuristic();
		terminalMessage.print("Deriving main Spring project required...");
		ProjectName projectName = projectNameHeuristic.deriveProjectName(description);
		terminalMessage.print("Done.  The code will primarily use " + projectName.getSpringProjectName());

		Map<String, String> context = getContext(description, projectName, projectPath);
		PromptRequest promptRequest = createPrompt(context);
		terminalMessage.print("Generating code ...");
		String response = generate(promptRequest);
		terminalMessage.print("Done.");

		String modifiedResponse = modifyResponse(response);
		writeReadMe(projectName, modifiedResponse, projectPath, terminalMessage);

		List<ProjectArtifact> projectArtifacts = createProjectArtifacts(modifiedResponse);
		processArtifacts(projectArtifacts, projectName, projectPath, context, terminalMessage);
	}

	private void writeReadMe(ProjectName projectName, String text, Path projectPath, TerminalMessage terminalMessage) {
		Path output = projectPath.resolve("README-ai-" + projectName.getShortPackageName() + ".md");
		if (output.toFile().exists()) {
			try {
				Files.delete(output);
			}
			catch (IOException ex) {
				throw new SpringCliException("Could not delete readme file: " + output.toAbsolutePath(), ex);
			}
		}
		try (Writer writer = new BufferedWriter(new FileWriter(output.toFile()))) {
			writer.write(text);
		}
		catch (IOException ex) {
			throw new SpringCliException("Could not write readme file: " + output.toAbsolutePath(), ex);
		}
		terminalMessage.print("Read the file " + "README-ai-" +
				projectName.getShortPackageName() + ".md for a description of the code that was added.");
	}

	private String modifyResponse(String response) {
		String code = "*Note: The code provided is just an example and may not be suitable for production use.*\n\n" + response;

		if (!code.contains("import javax")) {
			return code;
		}
		String regex = "^(import\\s+.*?)javax(\\.[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+)";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(code);
		return matcher.replaceAll("$1jakarta$2");
	}

	private static Path getProjectPath(String path) {
		Path projectPath;
		if (path == null) {
			projectPath = IoUtils.getWorkingDirectory();
		}
		else {
			projectPath = IoUtils.getProjectPath(path);
		}
		return projectPath;
	}

	@Override
	public String generate(PromptRequest promptRequest) {
		// get api token in file ~/.openai
		Properties properties = PropertyFileUtils.getPropertyFile();
		String apiKey = properties.getProperty("OPEN_AI_API_KEY");
		OpenAiService openAiService = new OpenAiService(apiKey, Duration.of(5, ChronoUnit.MINUTES));

		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
				.builder()
				.model("gpt-3.5-turbo")
				.temperature(0.8)
				.messages(
						List.of(
								new ChatMessage("system", promptRequest.getSystemPrompt()),
								new ChatMessage("user", promptRequest.getUserPrompt())))
				.build();

		StringBuilder builder = new StringBuilder();
		openAiService.createChatCompletion(chatCompletionRequest)
				.getChoices().forEach(choice -> {
					builder.append(choice.getMessage().getContent());
				});

		return builder.toString();
	}


	private Map<String, String> getContext(String description, ProjectName projectName,  Path path) {
		Map<String, String> context = new HashMap<>();
		context.put("build-tool", "maven");
		context.put("package-name", calculatePackage(projectName.getShortPackageName(), path));
		context.put("spring-project-name", projectName.getSpringProjectName());
		context.put("description", description);
		return context;
	}

	private String calculatePackageForArtifact(ProjectArtifact projectArtifact, String defaultPackage) {
		String packageToUse = defaultPackage;
		try (BufferedReader reader = new BufferedReader(new StringReader(projectArtifact.getText()))) {
			String firstLine = reader.readLine();
			if (firstLine.contains("package")) {
				String regex = "^package\\s+([a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*);";
				Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
				Matcher matcher = pattern.matcher(firstLine);
				// Find the package statement and extract the package name
				String packageName = "";
				if (matcher.find()) {
					packageToUse = matcher.group(1);
				}
			}
		} catch (IOException e) {
			throw new SpringCliException("Could not parse package name from Project Artifact: " +
					projectArtifact.getText());
		}
		return packageToUse;
	}
	private String calculatePackage(String shortPackageName, Path path) {
		Optional<String> rootPackage = RootPackageFinder.findRootPackage(path.toFile());
		if (rootPackage.isEmpty()) {
			throw new SpringCliException("Could not find root package from path " + path.toAbsolutePath());
		}
		return rootPackage.get() + ".ai." + shortPackageName;
	}


	@NotNull
	private static String getDefaultPackage(String shortPackageName, Path path) {
		Optional<String> rootPackage = RootPackageFinder.findRootPackage(path.toFile());
		if (rootPackage.isEmpty()) {
			throw new SpringCliException("Could not find root package from path " + path.toAbsolutePath());
		}
		return rootPackage.get() + ".ai." + shortPackageName;
	}

	List<ProjectArtifact> createProjectArtifacts(String response) {
		ProjectArtifactProcessor projectArtifactProcessor = new ProjectArtifactProcessor();
		List<ProjectArtifact> projectArtifacts = projectArtifactProcessor.process(response);
		return projectArtifacts;
	}

	private PromptRequest createPrompt(Map<String, String> context) {
		String systemPrompt = getPrompt(context, "system");
		String userPrompt = getPrompt(context, "user");
		return new PromptRequest(systemPrompt, userPrompt);
	}

	private String getPrompt(Map<String, String> context, String promptType) {
		ClassPathResource promptResource =
				new ClassPathResource("/org/springframework/cli/merger/ai/openai-" + promptType + "-prompt.txt");
		try {
			String promptRaw = StreamUtils.copyToString(promptResource.getInputStream(), UTF_8);
			return this.handlebarsTemplateEngine.process(promptRaw, context);
		}
		catch (IOException e) {
			throw new SpringCliException("Could not read resource " + promptResource);
		}
	}

	private void processArtifacts(List<ProjectArtifact> projectArtifacts, ProjectName projectName, Path projectPath, Map<String, String> context, TerminalMessage terminalMessage) {
		for (ProjectArtifact projectArtifact : projectArtifacts) {
			try {
				ProjectArtifactType artifactType = projectArtifact.getArtifactType();
				switch (artifactType) {
					case SOURCE_CODE:
						writeSourceCode(projectArtifact, projectName, context, projectPath);
						break;
					case TEST_CODE:
						writeTestCode(projectArtifact, projectName, context, projectPath);
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

	private void writeTestCode(ProjectArtifact projectArtifact, ProjectName projectName, Map<String, String> context, Path projectPath) throws IOException {
		// TODO paramaterize better to reduce code duplication
		String packageName = this.calculatePackageForArtifact(projectArtifact, context.get("package-name"));
		ClassNameExtractor classNameExtractor = new ClassNameExtractor();
		String className = classNameExtractor.extractClassName(projectArtifact.getText());
		Path output = createTestFile(projectPath, packageName, className + ".java");
		Files.createDirectories(output.getParent());
		try (Writer writer = new BufferedWriter(new FileWriter(output.toFile()))) {
			writer.write(projectArtifact.getText());
		}
	}

	private void writeSourceCode(ProjectArtifact projectArtifact, ProjectName projectName, Map<String, String> context, Path projectPath) throws IOException {
		String packageName = this.calculatePackageForArtifact(projectArtifact, context.get("package-name"));
		ClassNameExtractor classNameExtractor = new ClassNameExtractor();
		String className = classNameExtractor.extractClassName(projectArtifact.getText());
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
