package org.scm4j.commons.regexconfig;

import org.scm4j.commons.CommentedString;
import org.scm4j.commons.EConfig;
import org.scm4j.commons.URLContentLoader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;

public class RegexConfigLoaderYaml {

	public static final String URL_SEPARATOR = URLContentLoader.URL_SEPARATOR;
	public static final String OMAP_TAG = "!!omap";

	@SuppressWarnings("unchecked")
	public void loadFromUrls(LinkedHashMap<Object, Object> content, String... separatedUrls) throws IOException {
		Yaml yaml = new Yaml();
		URLContentLoader loader = new URLContentLoader();
		for (String separatedUrl : separatedUrls) {
			String[] urls = separatedUrl.split(URL_SEPARATOR);
			for (String url : urls) {
				if (url.isEmpty()) {
					continue;
				}
				String contentStr = loader.getContentFromUrl(url);

				if (!contentStr.isEmpty()) {
					contentStr = prependOmapIfNeed(contentStr, yaml);
					LinkedHashMap<Object, Object> map;
					try {
						map = (LinkedHashMap<Object, Object>) yaml.load(contentStr);
					} catch (Exception e) {
						throw new EConfig("failed to load config from yaml content at " + url + ": " + e.getMessage(), e);
					}
					if (map != null) {
						content.putAll(map);
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

	private boolean isSequence(String content, Yaml yaml) {
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
}
