package org.scm4j.commons;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class RegexConfigTest {
	
	private RegexConfig config;
	
	@Before
	public void setUp() throws IOException {
		config = new RegexConfig();
		String url1 = this.getClass().getResource("urls-omap.yml").toString();
		String url2 = this.getClass().getResource("urls-omap-bom.yml").toString();
		config.loadFromYamlUrls(url1, url2);
	}

	@Test
	public void testGetPropByName() {
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
	public void testGetPlaceholderedStringByName() {
		assertEquals("value4_placeholder", config.getPlaceholderedStringByName("node4placeholder", "prop4", null));
		assertEquals("unexisting_node", config.getPlaceholderedStringByName("unexisting_node", "placeholderedProp", null));
	}
}
