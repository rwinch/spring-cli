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


package org.springframework.cli.command;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cli.merger.ai.OpenAiHandler;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AiCommands {

	private final TerminalMessage terminalMessage;

	private OpenAiHandler openAiHandler = new OpenAiHandler();

	@Autowired
	public AiCommands(TerminalMessage terminalMessage) {
		this.terminalMessage = terminalMessage;
	}

	public AiCommands(OpenAiHandler openAiHandler, TerminalMessage terminalMessage) {
		this.terminalMessage = terminalMessage;
		this.openAiHandler = openAiHandler;
	}

	@ShellMethod(key = "ai add", value = "Add code to the project from AI for a Spring Project project.")
	public void aiAdd(
			@ShellOption(help = "The description of the code to create, this can be as short as a well known Spring project name, e.g JPA.", defaultValue = ShellOption.NULL, arity = 1)String description,
			@ShellOption(help = "Path to run the command in, most of the time this is not necessary to specify and the default value is the current working directory.", defaultValue = ShellOption.NULL, arity = 1) String path) {

		this.openAiHandler.add(description, path, terminalMessage);
	}
}
