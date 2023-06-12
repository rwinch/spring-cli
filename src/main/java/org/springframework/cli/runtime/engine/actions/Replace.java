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


package org.springframework.cli.runtime.engine.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Replace {

	private String regex;

	private boolean firstOccurance;

	private String value;

	private String path;

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public Replace(
			@JsonProperty("path") String path,
			@JsonProperty("regex") String regex,
			@JsonProperty("first-occurance") boolean firstOccurance,
			@JsonProperty("value") String value) {
		this.path = path;
		this.regex = regex;
		this.firstOccurance = firstOccurance;
		this.value = value;
	}

	public String getRegex() {
		return regex;
	}

	public boolean isFirstOccurance() {
		return firstOccurance;
	}

	public String getValue() {
		return value;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return "Replace{" +
				"regex='" + regex + '\'' +
				", firstOccurance=" + firstOccurance +
				", value='" + value + '\'' +
				", path='" + path + '\'' +
				'}';
	}
}
