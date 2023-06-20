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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cli.SpringCliException;
import org.springframework.cli.profile.ProfileService;
import org.springframework.cli.util.TerminalMessage;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.Table;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;
import org.springframework.util.StringUtils;

import static org.springframework.cli.profile.ProfileService.PROFILE_PATH;

@Command(command = "profile", group = "Profile")
public class ProfileCommands extends AbstractSpringCliCommands {

	private final TerminalMessage terminalMessage;

	private final ProfileService profileService = new ProfileService();

	public ProfileCommands(TerminalMessage terminalMessage) {
		this.terminalMessage = terminalMessage;
	}

	@Command(command = "add", description = "Add a profile")
	public void profileAdd(@Option(description = "Profile name", required = true) String name) {
		File profileFile = this.profileService.getFile(name);
		if (!profileFile.exists()) {
			try {
				this.profileService.createProfileDirectoryIfNecessary();
				profileFile.createNewFile();
				this.terminalMessage.print("YAML file for profile '" + name + "' created.");
			} catch (IOException e) {
				throw new SpringCliException("Error creating YAML file for profile '" + name + ".  " + e.getMessage());
			}
		} else {
			this.terminalMessage.print("YAML file for profile '" + name + "' already exists.");
		}
	}

	@Command(command = "remove", description = "Remove profile")
	public void profileRemove(@Option(description = "Profile name", required = true) String name) {
		File profileFile = this.profileService.getFile(name);
		if (profileFile.exists()) {
			if (profileFile.delete()) {
				this.terminalMessage.print("YAML file for profile '" + name + "' deleted.");
			} else {
				this.terminalMessage.print("Error creating YAML file for profile '" + name);
			}
		} else {
			this.terminalMessage.print("No YAML file for profile '" + name + "'.");
		}
	}

	@Command(command = "set", description = "Set a key value pair for a profile")
	public void profileSet(
			@Option(description = "A key", required = true) String key,
			@Option(description = "A value", required = true) Object value,
			@Option(description = "Profile name") String name) {
		if (!StringUtils.hasText(name)) {
			name = "";
		}
		this.profileService.updateProfile(name, key, value);
		this.terminalMessage.print("Key-value pair added to profile '" + name + "'");
	}

	@Command(command = "get", description = "Get the value of a key for a profile")
	public void profileGet(
			@Option(description = "Property key", required = true) String key,
			@Option(description = "Profile name") String name) {
		Map<String, Object> map = this.profileService.loadAsMap(name);
		if (! map.isEmpty()) {
			Object value = map.get(key);
			if (value != null) {
				this.terminalMessage.print(value.toString());
			} else {
				this.terminalMessage.print("Key '" + key + "' not found in profile '" + name + "'.");
			}
		} else {
			this.terminalMessage.print("YAML file for profile '" + name + "' does not exist.");
		}
	}

	@Command(command = "list", description = "List profiles")
	public Table profileList() {
		File directory = new File(PROFILE_PATH);
		List<String> profileNames = this.profileService.getProfileNames(directory);
		Stream<String[]> header = Stream.<String[]>of(new String[] { "Name" });
		Stream<String[]> rows;
		if (profileNames != null) {
			rows = profileNames.stream()
					.map(tr -> new String[] { tr }
					);
		} else {
			rows = Stream.empty();
		}
		List<String[]> allRows = rows.collect(Collectors.toList());
		String[][] data = Stream.concat(header, allRows.stream()).toArray(String[][]::new);
		TableModel model = new ArrayTableModel(data);
		TableBuilder tableBuilder = new TableBuilder(model);
		return tableBuilder.addFullBorder(BorderStyle.fancy_light).build();
	}

}
