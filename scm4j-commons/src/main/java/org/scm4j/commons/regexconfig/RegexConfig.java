package org.scm4j.commons.regexconfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegexConfig {

	private final LinkedHashMap<Object, Object> content = new LinkedHashMap<>();
	
	public void loadFromYamlUrls(String... separatedUrls) throws IOException {
		new RegexConfigLoaderYaml().loadFromUrls(content, separatedUrls);
	}

	@SuppressWarnings("unchecked")
	public <T> T getPropByName(String nameToMatch, String propName, T defaultValue) {
		for (Object key : content.keySet()) {
			if (key == null || nameToMatch.matches((String) key)) {
				Map<?, ?> props = (Map<?, ?>) content.get(key);
				if (props.containsKey(propName)) {
					return (T) props.get(propName);
				}
			}
		}
		return defaultValue;
	}

	public String getPlaceholderedStringByName(String nameToMatch, Object propName, String defaultValue) {
		String result = defaultValue;
		for (Object key : content.keySet()) {
			if (key == null || nameToMatch.matches((String) key)) {
				Map<?, ?> props = (Map<?, ?>) content.get(key);
				if (props.containsKey(propName)) {
					result = (String) props.get(propName);
					if (result != null)
						result = nameToMatch.replaceFirst(key == null ? ".*" : (String) key, result);
					break;
				}
			}
		}
		return result;
	}

	public Boolean isEmpty() {
		return content.isEmpty();
	}
}
