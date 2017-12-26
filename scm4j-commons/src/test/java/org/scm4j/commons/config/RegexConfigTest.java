package org.scm4j.commons.config;

import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.config.RegexConfig;

import java.io.IOException;

import static org.junit.Assert.*;

public class RegexConfigTest {
	
	private RegexConfig config;
	private String url1 = this.getClass().getResource("urls-omap.yml").toString();
	private String url2 = this.getClass().getResource("urls-omap-bom.yml").toString();
	private String url3 = this.getClass().getResource("urls-omap-last.yml").toString();
	private String url4 = this.getClass().getResource("urls-empty.yml").toString();
	private String url5 = this.getClass().getResource("urls-wrong-content.yml").toString();

	@Before
	public void setUp() {
		config = new RegexConfig();
	}

	@Test
	public void testGetPropByName() throws IOException {
		config.loadFromYamlUrls(url1, url2 + ";" + url3);
		assertEquals("value1and2", config.getPropByName("node1", "prop1and2", null));
		assertEquals("value1and2", config.getPropByName("node2", "prop1and2", null));
		assertEquals("default value", config.getPropByName("node1", "unexisting_prop", "default value"));
		assertEquals("default value", config.getPropByName("unexisting_node", "unexisting_prop", "default value"));
		assertEquals("value3", config.getPropByName("node3", "prop3", null));
		assertEquals("value3", config.getPropByName("node3AnySuffix", "prop3", null));
		assertEquals("defaultValue", config.getPropByName("unexisting_node", "defaultProp", null));
		int intValue = config.getPropByName("unexisting_node", "intProp", null);
		assertEquals(1, intValue);
		Boolean boolValue = config.getPropByName("unexisting_node", "booleanProp", null);
		assertEquals(true, boolValue);
	}
	
	@Test
	public void testGetPlaceholderedStringByName() throws IOException {
		config.loadFromYamlUrls(url1, url2 + ";" + url3);
		assertEquals("value4_placeholder", config.getPlaceholderedStringByName("node4placeholder", "prop4", null));
		assertEquals("unexisting_node", config.getPlaceholderedStringByName("unexisting_node", "placeholderedProp", null));
	}
	
	@Test
	public void testEmptyConfig() throws IOException {
		config.loadFromYamlUrls(url4);
		assertTrue(config.isEmpty());
	}

	@Test
	public void testEmptyUrls() throws IOException {
		config.loadFromYamlUrls("");
		assertTrue(config.isEmpty());
	}
	
	@Test
	public void testWrongContent() throws IOException {
		try {
			config.loadFromYamlUrls(url5);
			fail();
		} catch (EConfig e) {
			
		}
	}
}
