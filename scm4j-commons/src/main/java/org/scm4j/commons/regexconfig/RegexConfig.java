package org.scm4j.commons.regexconfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegexConfig {

	private final LinkedHashMap<Object, Object> content = new LinkedHashMap<>();
	
	public void loadFromYamlUrls(String... separatedUrls) {
		new RegexConfigLoaderYaml().loadFromUrls(content, separatedUrls);
	}

	@SuppressWarnings("unchecked")
	public <T> T getPropByName(String nameToMatch, String propName, T defaultValue) {
		for (Object key : content.keySet()) {
			if (key == null || nameToMatch.matches((String) key)) {
				LinkedHashMap<?, ?> props = getPropsMap(content, key);
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
				LinkedHashMap<?, ?> props = getPropsMap(content, key);
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

	private LinkedHashMap<?,?> getPropsMap(LinkedHashMap<Object, Object> content, Object key) {
		Object res = content.get(key);
		if (!(Map.class.isInstance(res))) {
			throw new EConfigWrongFormat("Wrong config format met by key " + key + ": ordered map only is supported");
		}
		return (LinkedHashMap<?, ?>) res;
	}

	public Boolean isEmpty() {
		return content.isEmpty();
	}
}
