package org.scm4j.commons;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class RegexConfig {
	public static final String URL_SEPARATOR = URLContentLoader.URL_SEPARATOR;
	private final LinkedHashMap<Object, Object> content = new LinkedHashMap<>();
	
	@SuppressWarnings("unchecked")
	public void loadFromYamlUrls(String... separatedUrls) throws IOException {
		Yaml yaml = new Yaml();
		URLContentLoader loader = new URLContentLoader();
		for (String separatedUrl : separatedUrls) {
			String[] urls = separatedUrl.split(URL_SEPARATOR);
			for (String url : urls) {
				if (url.isEmpty()) {
					continue;
				}
				String content = loader.getContentFromUrl(url);
				if (!content.isEmpty()) {
					LinkedHashMap<Object, Object> map;
					try {
						map = (LinkedHashMap<Object, Object>) yaml.load(content);
					} catch (Exception e) {
						throw new EConfig("failed to load config from yaml content at " + url + ": " + e.getMessage(), e);
					}
					if (map != null) {
						this.content.putAll(map);
					}
					
				}
			}
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

	public String getPlaceholderedStringByName(String nameToMatch, Object propName, String defaultValue) {
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

	public Boolean isEmpty() {
		return content.isEmpty();
	}
}
