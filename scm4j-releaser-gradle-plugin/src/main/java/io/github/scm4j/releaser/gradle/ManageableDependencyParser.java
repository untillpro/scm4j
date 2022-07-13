package io.github.scm4j.releaser.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManageableDependencyParser {
	private ManageableDependencyParser() {
	}

	public static class InvalidLineFormatException extends Exception {
		private final int lineNo;
		public InvalidLineFormatException(int lineNo) {
			this.lineNo = lineNo;
		}
		public int getLineNo() {
			return lineNo;
		}
	}

	public static List<ManageableDependency> parse(InputStream is) throws IOException, InvalidLineFormatException {
		if (is == null)
			return null;
		List<ManageableDependency> result = new ArrayList<>();
		Pattern pattern = Pattern.compile(String.format("%1$s(?<group>%2$s):(?<name>%2$s):(?<ver>%2$s)?"
				+ "(?::(?<clf>%2$s))?(?:@(?<ext>%2$s))?%1$s(?:#%1$s(?<cfg>%2$s)?%1$s)?",
				"[ \t]*", "[a-zA-Z0-9._-]+"));
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
			int lineNo = 0;
			String line;
			while ((line = br.readLine()) != null) {
				lineNo++;
				if (!line.trim().isEmpty() && !line.startsWith("#")) {
					Matcher m = pattern.matcher(line);
					if (m.matches()) {
						ManageableDependency mdep = new ManageableDependency();
						mdep.setGroup(m.group("group"));
						mdep.setName(m.group("name"));
						mdep.setClassifier(m.group("clf"));
						mdep.setExt(m.group("ext"));
						mdep.setVersion(m.group("ver"));
						mdep.setConfiguration(m.group("cfg"));
						result.add(mdep);
					} else {
						throw new InvalidLineFormatException(lineNo);
					}
				}
			}
		}
		return result;
	}

}
