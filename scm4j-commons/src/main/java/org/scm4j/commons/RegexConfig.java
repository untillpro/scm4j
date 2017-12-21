package org.scm4j.commons;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegexConfig {
	private final LinkedHashMap<Object, Object> content = new LinkedHashMap<>();

	public RegexConfig(LinkedHashMap<Object, Object>[] contents) {
		for (LinkedHashMap<Object, Object> content : contents) {
			this.content.putAll(content);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getPropByName(String nameToMatch, String propName, T defaultValue) {
		if (content != null) {
			for (Object key : content.keySet()) {
				if (key == null || nameToMatch.matches((String) key)) {
					Map<?, ?> props = (Map<?, ?>) content.get(key);
					if (props.containsKey(propName)) {
						return (T) props.get(propName);
					}
				}
			}
		}
		return defaultValue;
	}

	private String getPlaceholderedStringByName(String nameToMatch, Object propName, String defaultValue) {
		String result = defaultValue;
		if (content != null) {
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
		}
		return result;
	}
}
