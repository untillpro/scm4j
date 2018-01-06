package org.scm4j.commons;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

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

	@Test
	public void testPrependOmap() throws Exception {
		Yaml yaml = new Yaml();
		File seqOmap = new File(this.getClass().getResource("sequence-omap.yml").toURI());
		File mapping = new File(this.getClass().getResource("mapping.yml").toURI());
		File seq = new File(this.getClass().getResource("sequence.yml").toURI());
		File empty = new File(this.getClass().getResource("empty.yml").toURI());

		String content = config.prependOmapIfNeed(FileUtils.readFileToString(seqOmap, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfig.OMAP_TAG) == 1);

		content = config.prependOmapIfNeed(FileUtils.readFileToString(mapping, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfig.OMAP_TAG) == 0);

		content = config.prependOmapIfNeed(FileUtils.readFileToString(seq, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfig.OMAP_TAG) == 1);

		content = config.prependOmapIfNeed(FileUtils.readFileToString(empty, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfig.OMAP_TAG) == 0);
	}

	@Test
	public void testNoOmapTagOnEmptyFile() throws Exception {
		File empty = new File(this.getClass().getResource("empty.yml").toURI());
		assertTrue(config.noOMAPTag(FileUtils.readFileToString(empty, StandardCharsets.UTF_8)));
	}
}