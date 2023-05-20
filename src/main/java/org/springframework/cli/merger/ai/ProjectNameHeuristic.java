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

import org.apache.commons.lang3.StringUtils;

import org.springframework.cli.SpringCliException;

public class ProjectNameHeuristic {
	public ProjectName deriveProjectName(String project) {
		if (StringUtils.containsIgnoreCase(project,"jpa")) {
			return new ProjectName("jpa", "Spring Data JPA");
		}
		if (StringUtils.containsIgnoreCase(project,"mongo")) {
			return new ProjectName("mongodb", "Spring Data MongoDB");
		}
		throw new SpringCliException("Can't derive a Spring Project Name from the provided string " + project);
	}
}
