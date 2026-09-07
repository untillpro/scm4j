package org.scm4j.releaser.conf;

import org.apache.commons.io.FileUtils;
import org.scm4j.commons.Version;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DelayedTagsFile {
	
	public static final String MISSING_TO_STRING_MESSAGE = "<missing>";
	public static final String DELAYED_TAGS_FILE_NAME = "delayed-tags.yml";
	private static final String URL_PROPERTY = "url";
	private static final String SUBFOLDER_PROPERTY = "subfolder";
	private static final String VERSION_PROPERTY = "version";
	private static final String REVISION_PROPERTY = "revision";
	private final File delayedTagsFile;
	
	public DelayedTagsFile() {
		delayedTagsFile = new File(DELAYED_TAGS_FILE_NAME);
	}
	
	public DelayedTag getDelayedTagByUrl(String url) {
		return getDelayedTag(new VCSRepositoryId(url, null));
	}

	public DelayedTag getDelayedTag(VCSRepositoryId repositoryId) {
		return getRepositoryContent().get(repositoryId);
	}
	
	String loadContent() throws IOException {
		return FileUtils.readFileToString(delayedTagsFile, StandardCharsets.UTF_8);
	}
	
	void saveContent(String content) throws IOException {
		FileUtils.writeStringToFile(delayedTagsFile, content, StandardCharsets.UTF_8);
	}

	public Map<String, DelayedTag> getContent() {
		Map<String, DelayedTag> result = new LinkedHashMap<>();
		for (Map.Entry<VCSRepositoryId, DelayedTag> entry : getRepositoryContent().entrySet()) {
			result.put(entry.getKey().toString(), entry.getValue());
		}
		return result;
	}

	private Map<VCSRepositoryId, DelayedTag> getRepositoryContent() {
		if (!delayedTagsFile.exists()) {
			return new HashMap<>();
		}
		Yaml yaml = new Yaml();
		try {
			Object delayedTags = yaml.load(loadContent());
			if (delayedTags == null) {
				return new HashMap<>();
			}
			if (delayedTags instanceof List) {
				return recordsToDelayedTagsMap((List<?>) delayedTags);
			}
			if (delayedTags instanceof Map) {
				return legacyStringsToDelayedTagsMap((Map<?, ?>) delayedTags);
			}
			throw new IllegalArgumentException("Wrong delayed tags format");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Map<String, String>> delayedTagsMapToRecords(Map<VCSRepositoryId, DelayedTag> delayedTags) {
		List<Map<String, String>> result = new ArrayList<>();
		for (Map.Entry<VCSRepositoryId, DelayedTag> entry : delayedTags.entrySet()) {
			Map<String, String> record = new LinkedHashMap<>();
			record.put(URL_PROPERTY, entry.getKey().getUrl());
			if (!entry.getKey().getSubfolder().isEmpty()) {
				record.put(SUBFOLDER_PROPERTY, entry.getKey().getSubfolder());
			}
			record.put(REVISION_PROPERTY, entry.getValue().getRevision());
			record.put(VERSION_PROPERTY, entry.getValue().getVersion().toString());
			result.add(record);
		}
		return result;
	}

	private Map<VCSRepositoryId, DelayedTag> recordsToDelayedTagsMap(List<?> delayedTags) {
		Map<VCSRepositoryId, DelayedTag> result = new HashMap<>();
		for (Object value : delayedTags) {
			if (!(value instanceof Map)) {
				throw new IllegalArgumentException("Wrong delayed tag record format");
			}
			Map<?, ?> record = (Map<?, ?>) value;
			VCSRepositoryId repositoryId = new VCSRepositoryId((String) record.get(URL_PROPERTY),
					(String) record.get(SUBFOLDER_PROPERTY));
			DelayedTag tag = new DelayedTag(new Version((String) record.get(VERSION_PROPERTY)),
					(String) record.get(REVISION_PROPERTY));
			result.put(repositoryId, tag);
		}
		return result;
	}

	private Map<VCSRepositoryId, DelayedTag> legacyStringsToDelayedTagsMap(Map<?, ?> delayedTags) {
		Map<VCSRepositoryId, DelayedTag> result = new HashMap<>();
		for (Map.Entry<?, ?> entry : delayedTags.entrySet()) {
			Map<?, ?> record = (Map<?, ?>) entry.getValue();
			DelayedTag tag = new DelayedTag(new Version((String) record.get(VERSION_PROPERTY)),
					(String) record.get(REVISION_PROPERTY));
			result.put(new VCSRepositoryId((String) entry.getKey(), null), tag);
		}
		return result;
	}

	public void writeUrlDelayedTag(String url, Version version, String revision) throws IOException {
		writeDelayedTag(new VCSRepositoryId(url, null), version, revision);
	}

	public void writeDelayedTag(VCSRepositoryId repositoryId, Version version, String revision) throws IOException {
		if (!delayedTagsFile.exists()) {
			delayedTagsFile.createNewFile();
		}

		Map<VCSRepositoryId, DelayedTag> content = getRepositoryContent();
		DelayedTag tag = new DelayedTag(version, revision);
		DelayedTag previousTag = content.put(repositoryId, tag);
		if (!tag.equals(previousTag)) {
			writeContent(content);
		}
	}
	
	public boolean delete() {
		return delayedTagsFile.delete();
	}
	
	@Override
	public String toString() {
		if (!delayedTagsFile.exists()) {
			return MISSING_TO_STRING_MESSAGE;
		}
		return getContent().toString();
	}

	public void removeTagByUrl(String url) {
		removeTag(new VCSRepositoryId(url, null));
	}

	public void removeTag(VCSRepositoryId repositoryId) {
		Map<VCSRepositoryId, DelayedTag> content = getRepositoryContent();
		DelayedTag removedTag = content.remove(repositoryId);
		if (removedTag != null) {
			writeContent(content);
		}
	}

	private void writeContent(Map<VCSRepositoryId, DelayedTag> content) {
		Yaml yaml = new Yaml();
		try {
			saveContent(yaml.dump(delayedTagsMapToRecords(content)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
