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

import com.fasterxml.jackson.annotation.JsonProperty;

public class PomUpdate {

	private String projectName;

	private String projectVersion;

	private String projectDescription;

	public PomUpdate(@JsonProperty("project-name") String projectName,
			@JsonProperty("project-version") String projectVersion,
			@JsonProperty("project-description") String projectDescription) {
		this.projectName = projectName;
		this.projectVersion = projectVersion;
		this.projectDescription = projectDescription;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getProjectVersion() {
		return projectVersion;
	}

	public String getProjectDescription() {
		return projectDescription;
	}

	@Override
	public String toString() {
		return "PomUpdate{" +
				"projectName='" + projectName + '\'' +
				", projectVersion='" + projectVersion + '\'' +
				", projectDescription='" + projectDescription + '\'' +
				'}';
	}
}
