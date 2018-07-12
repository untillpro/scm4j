package org.scm4j.releaser.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

public class ReleaserGradlePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {

		// version
		File versionFile = project.file("version");
		if (!versionFile.exists())
			throw new GradleException("version file is not found");
		try {
			String version = new String(Files.readAllBytes(versionFile.toPath())).trim();
			project.setVersion(version);
		} catch (IOException e) {
			new GradleException("Error reading version file", e);
		}

		// mdeps
		File mdepsFile = project.file("mdeps");
		if (mdepsFile.exists()) {
			try (InputStream is = new FileInputStream(mdepsFile)) {
				for (ManagedDependency managedDependency : ManagedDependencyParser.parse(is)) {
					String configurationName = managedDependency.getConfiguration();
					if (configurationName == null)
						configurationName = "compile";
					Configuration configuration = project.getConfigurations().findByName(configurationName);
					if (configuration == null)
						configuration = project.getConfigurations().create(configurationName);
					Map<String, String> paramObject = new HashMap<>();
					paramObject.put("group", managedDependency.getGroup());
					paramObject.put("name", managedDependency.getName());
					paramObject.put("version", managedDependency.getVersion() != null ? managedDependency.getVersion()
							: "latest.integration");
					paramObject.put("classifier", managedDependency.getClassifier());
					paramObject.put("ext", managedDependency.getExt());
					if ("tests".equals(managedDependency.getClassifier()))
						paramObject.put("configuration", "test");
					Dependency dependency = project.getDependencies().create(paramObject);
					configuration.getDependencies().add(dependency);
				}
			} catch (IOException e) {
				new GradleException("Error reading mdeps file", e);
			}
		}

	}

}
