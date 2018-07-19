package org.scm4j.deployer.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.scm4j.releaser.gradle.ManagableDependency;
import org.scm4j.releaser.gradle.ManagableDependencyParser;

public class MdepsHelper {

	List<ManagableDependency> mdeps;

	public MdepsHelper(ClassLoader classLoader) {
		try (InputStream is = classLoader.getResourceAsStream("META-INF/mdeps")) {
			if (is == null)
				throw new RuntimeException("File 'META-INF/mdeps' is not found");
			mdeps = ManagableDependencyParser.parse(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getDepWithVersion(String configuration, String group, String name, String classifier, String ext) {
		for (ManagableDependency mdep : mdeps) {
			if (Objects.equals(group, mdep.getGroup()) && Objects.equals(name, mdep.getName())
					&& Objects.equals(classifier, mdep.getClassifier()) && Objects.equals(ext, mdep.getExt())
					&& Objects.equals(configuration, mdep.getConfiguration())) {
				String version = mdep.getVersion() == null ? "latest.integration" : mdep.getVersion();
				if (classifier != null && ext == null) ext = "jar";
				return group + ":" + name
						+ (ext == null ? "" : ":" + ext)
						+ (classifier == null ? "" : ":" + classifier)
						+ ":" + version;
			}
		}
		throw new RuntimeException(String.format("Dependency '%s:%s' is not found in mdeps", group, name));
	}

}
