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


package org.springframework.cli.runtime.engine.actions.handlers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cli.SpringCliException;
import org.springframework.cli.runtime.engine.actions.Replace;
import org.springframework.cli.runtime.engine.templating.TemplateEngine;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.util.StringUtils;

public class ReplaceActionHandler {

	private static final Logger logger = LoggerFactory.getLogger(ReplaceActionHandler.class);

	private TemplateEngine templateEngine;

	private Map<String, Object> model;

	private Path cwd;

	private TerminalMessage terminalMessage;
	public ReplaceActionHandler(TemplateEngine templateEngine, Map<String, Object> model, Path cwd, TerminalMessage terminalMessage) {
		this.templateEngine = templateEngine;
		this.model = model;
		this.cwd = cwd;
		this.terminalMessage = terminalMessage;
	}

	public void execute(Replace replace) {
		Path pathToModify = getPathToModify(replace, templateEngine, model, cwd);
		modify(pathToModify, templateEngine, replace, model);
	}

	private void modify(Path pathToModify, TemplateEngine templateEngine, Replace replace, Map<String, Object> model) {
		try {
			String fileContents = Files.readString(pathToModify);
			// TODO check regex isn't empty
			Pattern pattern = Pattern.compile(replace.getRegex());
			Matcher matcher = pattern.matcher(fileContents);
			StringBuffer updatedContents  = new StringBuffer();
			//TODO check empty valures
			String rawValue = replace.getValue();
			String valueToUse = templateEngine.process(rawValue, model);

			if (replace.isFirstOccurance()) {
				if (matcher.find()) {
					matcher.appendReplacement(updatedContents, valueToUse);
				}
				matcher.appendTail(updatedContents);
			}

			Path newFile = Paths.get(pathToModify + ".new");
			// write updated file to with .new suffix.
			// TODO investigate specifying charset.
			Files.write(newFile, updatedContents.toString().getBytes());
			// swap out files
			Files.copy(newFile, pathToModify, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			// delete newFile
			deleteFile(newFile);

		} 	catch (IOException ex) {
			terminalMessage.print("Could not modify the file " + pathToModify.toFile().getAbsolutePath() + ".  Exception Message = " + ex.getMessage());
		}
	}

	private void deleteFile(Path newFile) {
		if (Files.exists(newFile)) {
			try {
				Files.delete(newFile);
			}
			catch (IOException e) {
				logger.error("Could not delete file {}", newFile);
			}
		}
	}

	private Path getPathToModify(Replace replace, TemplateEngine templateEngine, Map<String, Object> model, Path cwd) {
		if (!StringUtils.hasText(replace.getPath())) {
			throw new SpringCliException("Replace action does not have a 'path:' field.");
		}
		String fileNameToInject = templateEngine.process(replace.getPath(), model);
		if (!StringUtils.hasText(fileNameToInject)) {
			throw new SpringCliException("Replace action can not be performed because the value of the 'path:' field resolved to an empty string.");
		}
		Path pathToFile = cwd.resolve(fileNameToInject).toAbsolutePath();
		if ((!pathToFile.toFile().exists())) {
			throw new SpringCliException("Replace action can not be performed because the file " + pathToFile + " does not exist.");
		}
		if ((pathToFile.toFile().isDirectory())) {
			throw new SpringCliException("Replace action can not be performed because the path " + pathToFile + " is a directory, not a file.");
		}
		return pathToFile;
	}
}
