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

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cli.SpringCliException;
import org.springframework.cli.roles.RoleService;
import org.springframework.cli.runtime.engine.actions.Options;
import org.springframework.cli.runtime.engine.actions.Question;
import org.springframework.cli.runtime.engine.actions.Vars;
import org.springframework.cli.runtime.engine.templating.TemplateEngine;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.ComponentFlow.Builder;
import org.springframework.shell.component.flow.ComponentFlow.ComponentFlowResult;
import org.springframework.shell.component.flow.ResultMode;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.style.TemplateExecutor;
import org.springframework.shell.style.Theme;
import org.springframework.shell.style.ThemeRegistry;
import org.springframework.shell.style.ThemeResolver;
import org.springframework.shell.style.ThemeSettings;

import static org.springframework.cli.util.JavaUtils.inferType;

public class VarsActionHandler {

	private static final Logger logger = LoggerFactory.getLogger(VarsActionHandler.class);

	private TemplateExecutor templateExecutor	;

	private DefaultResourceLoader resourceLoader;

	private TemplateEngine templateEngine;

	private Map<String, Object> model;

	@Nullable
	private Path dynamicSubCommandPath;

	private TerminalMessage terminalMessage;

	private Terminal terminal;

	public VarsActionHandler(TemplateEngine templateEngine, Map<String, Object> model, Path dynamicSubCommandPath, TerminalMessage terminalMessage, Terminal terminal) {
		this.templateEngine = templateEngine;
		this.model = model;
		this.dynamicSubCommandPath = dynamicSubCommandPath;
		this.terminalMessage = terminalMessage;
		this.terminal = terminal;
		createResourceLoaderAndTemplateExecutor();
	}

	public void execute(Vars vars) {
		validate(vars);
		List<Question> questions = vars.getQuestions();
		for (Question question : questions) {
			processQuestion(question);
		}

	}

	private void validate(Vars vars) {
		List<Question> questions = vars.getQuestions();
		ensureValidType(questions);
	}

	private void ensureValidType(List<Question> questions) {
		for (Question question : questions) {
			if (!isValidType(question.getType())) {
				throw new SpringCliException("Invalid type '" + question.getType() +
						"' for question with label '" + question.getLabel() + "'.");
			}
		}
	}

	private boolean isValidType(String type) {
		return type.equals("input") || type.equals("dropdown") || type.equals("path");
	}

	private void processQuestion(Question question) {

		Builder builder = ComponentFlow.builder().reset()
				.terminal(this.terminal)
				.resourceLoader(this.resourceLoader)
				.templateExecutor(this.templateExecutor);
		String type = question.getType();
		if ("input".equals(type)) {
			processInputQuestion(question, builder);
		}
		if ("dropdown".equals(type)) {
			processDropdownQuestion(question, builder);
		}
		if ("path".equals(type)) {
			processPathQuestion(question, builder);
		}
	}

	private void processInputQuestion(Question question, Builder builder) {
		String resultValue = "";

				// now the good stuff
		ComponentFlow componentFlow = builder
				.withStringInput(question.getName()) //this is the variable name
				.name(question.getLabel()) // This is the text string the user sees.
				.resultValue(resultValue)
				.resultMode(ResultMode.ACCEPT)
				.and().build();

		ComponentFlowResult componentFlowResult = componentFlow.run();
		ComponentContext<?> resultContext = componentFlowResult.getContext();

		Object object = resultContext.get(question.getName());
		System.out.println("Collected '" + object + "' as value.  Inferred type " + inferType(object).getClass());

		// store in default role for now
		RoleService roleService = new RoleService();
		roleService.updateRole("", question.getName(), object);

	}


	private void processPathQuestion(Question question, Builder builder) {

	}

	private void processDropdownQuestion(Question question, Builder builder) {
		String resultValue = "";
		boolean isMultiple = false;
		if (question.getAttributes() != null) {
			isMultiple = question.getAttributes().isMultiple();
		}
		if (isMultiple) {
			processMultiItemSelector(question, builder);
		} else {
			processSingleItemSelector(question, builder);
		}
	}

	private void processSingleItemSelector(Question question, Builder builder) {
		String resultValue = "";
		Map<String, String> options = getOptions(question.getOptions());
		ComponentFlow componentFlow = builder.withSingleItemSelector(question.getName())
				.name(question.getLabel())
				.resultValue(resultValue)
				.resultMode(ResultMode.ACCEPT)
				.selectItems(options)
				.sort(NAME_COMPARATOR)
				.and().build();


		ComponentFlowResult componentFlowResult = componentFlow.run();
		ComponentContext<?> resultContext = componentFlowResult.getContext();

		Object object = resultContext.get(question.getName());
		System.out.println("Collected '" + object + "' as value.  Inferred type " + inferType(object).getClass());

		// store in default role for now
		RoleService roleService = new RoleService();
		roleService.updateRole("", question.getName(), object);
		System.out.println("Saved to default role in vars.yml");
	}
	private final static Comparator<SelectorItem<String>> NAME_COMPARATOR = (o1, o2) -> {
		return o1.getName().compareTo(o2.getName());
	};

	private Map<String, String> getOptions(Options options) {
		if (options.getExec() == null) {
			return new HashMap<>();
		}

		ExecActionHandler execActionHandler = new ExecActionHandler(this.templateEngine, this.model, this.dynamicSubCommandPath, this.terminalMessage);
		Map<String, Object> outputs = new HashMap<>();
		execActionHandler.executeShellCommand(options.getExec(), outputs);

		if (!outputs.containsKey(ExecActionHandler.OUTPUT_STDOUT_JSONPATH)) {
			return new HashMap<>(0);
		}

		//determine if output is a list or a map
		Object jsonPathObject = outputs.get(ExecActionHandler.OUTPUT_STDOUT_JSONPATH);
		if (jsonPathObject instanceof String) {
			Map<String, String> map = new HashMap<>();
			map.put(jsonPathObject.toString(), jsonPathObject.toString());
			return map;
		}
		if (jsonPathObject instanceof Map) {
			// TODO review
			return (Map<String,String>)jsonPathObject;
		}
		else if (jsonPathObject instanceof List) {
			Map<String, String> map = new HashMap<>();
			List list = (List)jsonPathObject;
			for (Object listItem : list) {
				map.put(listItem.toString(), listItem.toString());
			}
			return map;
		}
		else {
			throw new SpringCliException("Can not convert the JSON Path output to a Map for use in dropdown items in question.  JSON Path output = " + jsonPathObject);
		}
	}

	private void processMultiItemSelector(Question question, Builder builder) {

	}


	private void createResourceLoaderAndTemplateExecutor() {

		ThemeRegistry themeRegistry = new ThemeRegistry();
		themeRegistry.register(new Theme() {
			@Override
			public String getName() {
				return "default";
			}

			@Override
			public ThemeSettings getSettings() {
				return ThemeSettings.defaults();
			}
		});
		ThemeResolver themeResolver = new ThemeResolver(themeRegistry, "default");
		this.templateExecutor = new TemplateExecutor(themeResolver);

		this.resourceLoader = new DefaultResourceLoader();
	}
}
