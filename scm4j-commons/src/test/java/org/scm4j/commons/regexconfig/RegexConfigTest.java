package org.scm4j.commons.regexconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.EConfig;

public class RegexConfigTest {

	private RegexConfig config;
	private String seqOmap = this.getClass().getResource("sequence-omap.yml").toString();
	private String mapping = this.getClass().getResource("mapping.yml").toString();
	private String seq = this.getClass().getResource("sequence.yml").toString();
	private String empty = this.getClass().getResource("empty.yml").toString();
	private String wrongContent = this.getClass().getResource("wrong-content.yml").toString();
	private String seqBOM = this.getClass().getResource("sequence-bom.yml").toString();

	@Before
	public void setUp() {
		config = new RegexConfig();
	}

	@Test
	public void testGetPropByName() throws IOException {
		config.loadFromYamlUrls(seqOmap, seqBOM + ";" + seq + ";" + mapping);
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
		config.loadFromYamlUrls(seqOmap, seqBOM + ";" + seq);
		assertEquals("value4_placeholder", config.getPlaceholderedStringByName("node4placeholder", "prop4", null));
		assertEquals("unexisting_node", config.getPlaceholderedStringByName("unexisting_node", "placeholderedProp", null));
	}

	@Test
	public void testEmptyConfig() throws IOException {
		config.loadFromYamlUrls(empty);
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
			config.loadFromYamlUrls(wrongContent);
			fail();
		} catch (EConfig e) {
		}
	}
}