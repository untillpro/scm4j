package org.scm4j.commons.regexconfig;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class RegexConfigLoaderYamlTest {

	@Test
	public void testPrependOmap() throws Exception {
		RegexConfigLoaderYaml loader = new RegexConfigLoaderYaml();
		Yaml yaml = new Yaml();
		File seqOmap = new File(this.getClass().getResource("sequence-omap.yml").toURI());
		File mapping = new File(this.getClass().getResource("mapping.yml").toURI());
		File seq = new File(this.getClass().getResource("sequence.yml").toURI());
		File empty = new File(this.getClass().getResource("empty.yml").toURI());

		String content = loader.prependOmapIfNeed(FileUtils.readFileToString(seqOmap, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfigLoaderYaml.OMAP_TAG) == 1);

		content = loader.prependOmapIfNeed(FileUtils.readFileToString(mapping, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfigLoaderYaml.OMAP_TAG) == 0);

		content = loader.prependOmapIfNeed(FileUtils.readFileToString(seq, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfigLoaderYaml.OMAP_TAG) == 1);

		content = loader.prependOmapIfNeed(FileUtils.readFileToString(empty, StandardCharsets.UTF_8), yaml);
		assertTrue(StringUtils.countMatches(content, RegexConfigLoaderYaml.OMAP_TAG) == 0);
	}

	@Test
	public void testNoOmapTagOnEmptyFile() throws Exception {
		RegexConfigLoaderYaml loader = new RegexConfigLoaderYaml();
		File empty = new File(this.getClass().getResource("empty.yml").toURI());
		assertTrue(loader.noOMAPTag(FileUtils.readFileToString(empty, StandardCharsets.UTF_8)));
	}

}