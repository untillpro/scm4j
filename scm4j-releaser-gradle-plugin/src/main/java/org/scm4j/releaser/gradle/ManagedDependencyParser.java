package org.scm4j.releaser.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManagedDependencyParser {
	private ManagedDependencyParser() {
	}

	public static List<ManagedDependency> parse(InputStream is) throws IOException {
		if (is == null)
			return null;
		List<ManagedDependency> result = new ArrayList<>();
		Pattern pattern = Pattern.compile(String.format("%1$s(?<group>%2$s):(?<name>%2$s):(?<ver>%2$s)?"
				+ "(?::(?<clf>%2$s))?(?:@(?<ext>%2$s))?%1$s(?:#%1$s(?<cfg>%2$s)?%1$s)?",
				"[ \t]*", "[a-zA-Z0-9._-]+"));
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
			String line;
			while ((line = br.readLine()) != null) {
				Matcher m = pattern.matcher(line);
				if (m.matches()) {
					ManagedDependency managedDependency = new ManagedDependency();
					managedDependency.setGroup(m.group("group"));
					managedDependency.setName(m.group("name"));
					managedDependency.setClassifier(m.group("clf"));
					managedDependency.setExt(m.group("ext"));
					managedDependency.setVersion(m.group("ver"));
					managedDependency.setConfiguration(m.group("cfg"));
					result.add(managedDependency);
				}
				// TODO error handle
			}
		}
		return result;
	}

}
