package org.springframework.cli.merger.ai;

import org.junit.jupiter.api.Test;

import org.springframework.cli.merger.ai.service.ProjectNameHeuristicAiService;
import org.springframework.cli.util.TerminalMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class ProjectNameHeuristicAiServiceTest {

	@Test
	void deriveProjectName() {
		ProjectNameHeuristicAiService projectNameHeuristic = new ProjectNameHeuristicAiService(TerminalMessage.noop());

		ProjectName projectName = projectNameHeuristic.deriveProjectName("jpa");

		assertThat(projectName.getSpringProjectName()).isEqualTo("Spring Data JPA");

//		projectName = projectNameHeuristic.deriveProjectName("JpA");
//		assertThat(projectName.getShortPackageName()).isEqualTo("jpa");
//		assertThat(projectName.getSpringProjectName()).isEqualTo("Spring Data JPA");
//		assertThatThrownBy(() -> {
//			projectNameHeuristic.deriveProjectName("foo");
//		}).isInstanceOf(SpringCliException.class)
//				.hasMessageContaining("Can't derive a Spring Project Name from the provided string " + "foo");
	}
}