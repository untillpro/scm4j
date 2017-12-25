package org.scm4j.commons;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class URLContentLoader {
	
	public static final String URL_SEPARATOR = ";";
	public static final String DEFAULT_PROTOCOL = "file:///";
	
	public List<String> getContentsFromUrls(List<URL> urls) throws IOException {
		List<String> res = new ArrayList<>();
		for (URL url : urls) {
			res.add(getContentFromUrl(url));
		}
		return res;
	}

	public List<String> getContentsFromUrlStrings(List<String> separatedUrls) throws IOException {
		List<String> res = new ArrayList<>();
		for (String separatedUrl : separatedUrls) {
			String[] urls = separatedUrl.split(URL_SEPARATOR);
			for (String url : urls) {
				res.add(getContentFromUrl(url));
			}
		}
		return res;
	}
	
	private String getWithDefaultProtocol(String urlStr) {
		if (!urlStr.trim().toLowerCase().startsWith("file:") && !urlStr.trim().toLowerCase().startsWith("http:") && !urlStr.trim().toLowerCase().startsWith("https:")) {
			return DEFAULT_PROTOCOL + urlStr;
		}
		return urlStr;
	}
	
	public String getContentFromUrl(URL url) throws IOException {
		try (InputStream inputStream = url.openStream()) {
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
		}
	}
	
	public String getContentFromUrl(String url) throws IOException {
		return getContentFromUrl(new URL(getWithDefaultProtocol(url)));
	}
}
