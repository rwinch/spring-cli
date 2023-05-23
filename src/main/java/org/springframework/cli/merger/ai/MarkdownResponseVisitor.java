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

import java.util.ArrayList;
import java.util.List;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Paragraph;

import org.springframework.cli.SpringCliException;

public class MarkdownResponseVisitor extends AbstractVisitor {

	private List<ProjectArtifact> projectArtifacts = new ArrayList<>();

	@Override
	public void visit(Paragraph paragraph) {
		super.visit(paragraph);
	}

	@Override
	public void visit(FencedCodeBlock fencedCodeBlock) {
		super.visit(fencedCodeBlock);
		String info = fencedCodeBlock.getInfo();
		String code = fencedCodeBlock.getLiteral();
		int projectArtifactCountBefore = projectArtifacts.size();
		if (info.equalsIgnoreCase("java")) {
			addJavaCode(code);
		}
		if (info.equalsIgnoreCase("xml")) {
			addMavenDependencies(code);
		}
		if (info.isBlank()) {
			// sometimes the response doesn't contain
			if (code.contains("package")) {
				addJavaCode(code);
			} else if (code.contains("<dependency>")) {
				addMavenDependencies(code);
			}
		}

		// Check that we processed all fenced code blocks
		if (this.projectArtifacts.size() == projectArtifactCountBefore) {
			System.out.println("Could not classify FencedCodeBlock with info,code = " + info + "\n" + code);
		}
	}

	private void addMavenDependencies(String code) {
		this.projectArtifacts.add(new ProjectArtifact(ProjectArtifactType.MAVEN_DEPENDENCIES, code));
	}

	private void addJavaCode(String code) {
		if (code.contains("@SpringBootApplication")) {
			this.projectArtifacts.add(new ProjectArtifact(ProjectArtifactType.MAIN_CLASS, code));
		} else if (code.contains("@Test")) {
			this.projectArtifacts.add(new ProjectArtifact(ProjectArtifactType.TEST_CODE, code));
		} else {
			this.projectArtifacts.add(new ProjectArtifact(ProjectArtifactType.SOURCE_CODE, code));
		}
	}

	public List<ProjectArtifact> getProjectArtifacts() {
		return this.projectArtifacts;
	}
}
