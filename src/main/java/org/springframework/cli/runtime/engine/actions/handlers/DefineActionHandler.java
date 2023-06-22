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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cli.runtime.engine.actions.Define;
import org.springframework.cli.runtime.engine.actions.From;
import org.springframework.cli.runtime.engine.actions.Question;
import org.springframework.cli.runtime.engine.actions.Var;
import org.springframework.cli.runtime.engine.templating.TemplateEngine;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.ComponentFlow.ComponentFlowResult;
import org.springframework.shell.component.flow.ResultMode;

import static org.springframework.cli.util.JavaUtils.inferType;

public class DefineActionHandler {

	private static final Logger logger = LoggerFactory.getLogger(DefineActionHandler.class);

	private TemplateEngine templateEngine;

	private Map<String, Object> model;

	private TerminalMessage terminalMessage;

	public DefineActionHandler(TemplateEngine templateEngine, Map<String, Object> model, TerminalMessage terminalMessage) {
		this.templateEngine = templateEngine;
		this.model = model;
		this.terminalMessage = terminalMessage;
	}

	public void execute(Define define) {
		Var var = define.getVar();
		From from = var.getFrom();
		Question question = from.getQuestion();
		String questionText = question.getText();
		String variableName = question.getName();

		String resultValue = "";
		ComponentFlow wizard = ComponentFlow.builder().reset()
				.withStringInput(variableName) //this is the variable name
				.name(questionText) // This is the text string the user sees.
				.resultValue(resultValue)
				.resultMode(ResultMode.ACCEPT)
				.and().build();

		ComponentFlowResult componentFlowResult = wizard.run();
		ComponentContext<?> resultContext = componentFlowResult.getContext();

		Object object = resultContext.get(variableName);
		System.out.println("Collected " + object + " as value.  Inferred type " + inferType(object).getClass());
	}
}
