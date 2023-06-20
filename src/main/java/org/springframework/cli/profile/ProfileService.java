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


package org.springframework.cli.profile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.config.YamlProcessor.ResolutionMethod;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cli.SpringCliException;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

public class ProfileService {

	public static String PROFILE_PATH = ".spring/profiles";

	public Map<String, Object> loadAsMap(String name) {
		YamlMapFactoryBean factory = new YamlMapFactoryBean();
		factory.setResolutionMethod(ResolutionMethod.OVERRIDE_AND_IGNORE);
		factory.setResources(new FileSystemResource(getFile(name)));
		Map<String, Object> map = factory.getObject();
		return map;
	}

	public void updateProfile(String profileName, String key, Object value) {
		Map<String, Object> map = loadAsMap(profileName);
		Object valueToUse = inferType(value);
		map.put(key, valueToUse);

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		dumperOptions.setPrettyFlow(true);
		dumperOptions.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak());
		Yaml yaml = new Yaml(dumperOptions);
		File profileFile = getFile(profileName);
		try {
			yaml.dump(map, new PrintWriter(profileFile));
		}
		catch (FileNotFoundException e) {
			throw new SpringCliException("The YAML file for the profile '" + profileName
					+ "' was not found.  Error = " + e.getMessage());
		}

	}

	private Object inferType(Object value) {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		try {
			return objectMapper.readValue(value.toString(), Object.class);
		} catch (IOException e) {
			return value.toString();
		}
	}

	public List<String> getProfileNames(File directory) {
		List<String> profileNames = new ArrayList<>();
		if (directory.exists() && directory.isDirectory()) {
			File[] files = directory.listFiles();
			Pattern pattern = Pattern.compile("cli-(.*?)\\.(yml|yaml)");
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						String filename = file.getName();
						Matcher matcher = pattern.matcher(filename);
						if (matcher.matches()) {
							String profileName = matcher.group(1);
							profileNames.add(profileName);
						}
					}
				}
			}
		}
		return profileNames;
	}

	public void createProfileDirectoryIfNecessary() {
		File directory = new File(PROFILE_PATH);
		if (!directory.exists()) {
			directory.mkdirs();
		}
	}

	public File getFile(String name) {
		String fileName;
		if (StringUtils.hasText(name)) {
			fileName = "cli-" + name + ".yml";
		} else {
			fileName = "cli.yml";
		}
		String filePath = PROFILE_PATH + File.separator + fileName;
		File propertiesFile = new File(filePath);
		return propertiesFile;
	}
}
