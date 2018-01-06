package org.scm4j.commons;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegexConfig {
	public static final String URL_SEPARATOR = URLContentLoader.URL_SEPARATOR;
	public static final String OMAP_TAG = "!!omap";

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
					content = prependOmapIfNeed(content, yaml);
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

	String prependOmapIfNeed(String content, Yaml yaml) throws IOException {
		if (isSequence(content, yaml)) {
			if (noOMAPTag(content)) {
				return OMAP_TAG + "\r\n" + content;
			}
		}
		return content;
	}

	boolean isSequence(String content, Yaml yaml) {
		StringReader sr = new StringReader(content);
		Node node = yaml.compose(sr);
		return node != null && node.getNodeId().equals(NodeId.sequence);
	}

	boolean noOMAPTag(String content) throws IOException {
		StringReader sr = new StringReader(content);
		BufferedReader br = new BufferedReader(sr);
		String line = br.readLine();
		while (line != null) {
			CommentedString cs = new CommentedString(line);
			if (cs.isValuable()) {
				return !cs.getStrNoComment().trim().startsWith(OMAP_TAG);
			}
			line = br.readLine();
		}
		return true;
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
