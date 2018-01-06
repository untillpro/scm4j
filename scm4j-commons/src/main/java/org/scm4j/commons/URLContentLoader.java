package org.scm4j.commons;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

	public List<String> getContentsFromUrls(String... separatedUrls) throws IOException {
		List<String> res = new ArrayList<>();
		for (String separatedUrl : separatedUrls) {
			if (separatedUrl.isEmpty()) {
				continue;
			}
			String[] urls = separatedUrl.split(URL_SEPARATOR);
			for (String url : urls) {
				String content = getContentFromUrl(url);
				if (content != null) {
					res.add(content);
				}
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
			BOMInputStream bOMInputStream = new BOMInputStream(inputStream);
			ByteOrderMark bom = bOMInputStream.getBOM();
			String charsetName = bom == null ? StandardCharsets.UTF_8.toString() : bom.getCharsetName();
			return IOUtils.toString(bOMInputStream, charsetName);
		}
	}
	
	public String getContentFromUrl(String url) throws IOException {
		if (url.isEmpty()) {
			return null;
		}
		return getContentFromUrl(new URL(getWithDefaultProtocol(url)));
	}
}
