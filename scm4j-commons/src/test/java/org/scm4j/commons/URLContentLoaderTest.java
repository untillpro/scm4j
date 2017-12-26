package org.scm4j.commons;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class URLContentLoaderTest {
	
	private static final String URLS_OMAP_BOM_FILE = "urls-omap-bom.yml";
	private static final String URLS_OMAP_FILE = "urls-omap.yml";
	private static final String URLS_OMAP_LAST_FILE = "urls-omap-last.yml";

	@Test
	public void testGetContentFromUrls() throws Exception {
		URLContentLoader loader = new URLContentLoader();
		URL url1 = this.getClass().getResource(URLS_OMAP_FILE);
		URL url2 = this.getClass().getResource(URLS_OMAP_BOM_FILE);
		URL url3 = this.getClass().getResource(URLS_OMAP_LAST_FILE);
		String content1 = FileUtils.readFileToString(new File(url1.toURI()), StandardCharsets.UTF_8);
		String content2 = FileUtils.readFileToString(new File(url2.toURI()), StandardCharsets.UTF_8);
		String content3 = FileUtils.readFileToString(new File(url3.toURI()), StandardCharsets.UTF_8);
		List<String> contents = loader.getContentsFromUrls(url1.toString(), url2.toString() + URLContentLoader.URL_SEPARATOR + url3.toString());
		assertEquals(String.join("", content1, content2, content3), String.join("", contents));
		
		contents = loader.getContentsFromUrls(Arrays.asList(url1, url2));
		assertEquals(String.join("", content1, content2), String.join("",  contents));
		
		contents = loader.getContentsFromUrls("", URLContentLoader.URL_SEPARATOR + url1.toString());
		assertEquals(content1, String.join("",  contents));
	}
	
	@Test
	public void testFileProtocolOmitting() throws Exception {
		URLContentLoader loader = new URLContentLoader();
		URL url1 = this.getClass().getResource(URLS_OMAP_FILE);
		URL url2 = this.getClass().getResource(URLS_OMAP_BOM_FILE);
		File file1 = new File(url1.toURI());
		File file2 = new File(url2.toURI());
		String content1 = FileUtils.readFileToString(file1, StandardCharsets.UTF_8);
		String content2 = FileUtils.readFileToString(file2, StandardCharsets.UTF_8);
		List<String> contents = loader.getContentsFromUrls(file1.toString(), file2.toString());
		assertEquals(String.join("", content1, content2), String.join("",  contents));
	}
}

