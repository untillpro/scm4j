package org.scm4j.commons.regexconfig;

import org.scm4j.commons.URLContentLoader;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;

public class RegexConfigLoaderYaml {

	public static final String URL_SEPARATOR = URLContentLoader.URL_SEPARATOR;

	@SuppressWarnings("unchecked")
	public void loadFromUrls(LinkedHashMap<Object, Object> content, String... separatedUrls) {
		Yaml yaml = new Yaml();
		URLContentLoader loader = new URLContentLoader();
		for (String separatedUrl : separatedUrls) {
			String[] urls = separatedUrl.split(URL_SEPARATOR);
			for (String url : urls) {
				if (url.isEmpty()) {
					continue;
				}
				Object res;
				String contentStr;
				try {
					contentStr = loader.getContentFromUrl(url);
				} catch (Exception e) {
					throw new EConfigReadFailed("Failed to read config from url " + url + ": "+ e.getMessage(), e);
				}
				if (contentStr.isEmpty()) {
					continue;
				}
				try {
					res = yaml.load(contentStr);
				} catch (Exception e) {
					throw new EConfigParseFailed("Failed to parse config from yaml content at " + url + ": " + e.getMessage(), e);
				}
				if (res != null) {
					if (!(res instanceof LinkedHashMap)) {
						throw new EConfigWrongFormat("Unexpected yaml format at " + url + ": ordered map only is supported");
					}
					content.putAll((LinkedHashMap<Object, Object>) res);
				}
			}
		}
	}
}
