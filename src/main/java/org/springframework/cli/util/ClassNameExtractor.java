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


package org.springframework.cli.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cli.SpringCliException;

public class ClassNameExtractor {

	private String[] patternStrings = {
			"(?<=\\bclass\\s)\\w+",
			"(?<=\\binterface\\s)\\w+",
			"(?<=\\b@interface\\s)\\w+",
			"(?<=\\benum\\s)\\w+"
	};

	private List<Pattern> patterns = new ArrayList<>();

	public ClassNameExtractor() {
		for (String patternString : patternStrings) {
			patterns.add(Pattern.compile(patternString));
		}
	}

	public Optional<String> extractClassName(String code) {
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(code);
			if (matcher.find()) {
				return Optional.of(matcher.group());
			}
		}
		return Optional.empty();
	}
}
