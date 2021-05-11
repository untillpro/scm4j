package org.scm4j.releaser.gradle;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.PluginApplicationException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReleaserGradlePluginTest {

	@Rule public TemporaryFolder testProjectDir = new TemporaryFolder();

    private Project createProject() {
    	Project project = ProjectBuilder.builder()
				.withProjectDir(testProjectDir.getRoot())
				.build();
    	Map<String, Class<?>> arg = new HashMap<>();	
    	arg.put("plugin", ReleaserGradlePlugin.class);
    	project.apply(arg);
    	return project;
    }	

	private void createFile(String fileName, String fileContent) throws IOException {
		File file = testProjectDir.newFile(fileName);
		Files.write(file.toPath(), fileContent.getBytes());
	}

	@Test public void noVersionFile() {
		Exception e = assertThrows(PluginApplicationException.class, () -> {
			createProject();
		});
		assertThat(e.getCause(),
				allOf(
						instanceOf(GradleException.class),
						hasMessage(containsString("version file is not found"))
				)
		);
	}

	@Test public void version() throws Exception {
		createFile("version", "1.1");
		Project project = createProject();
		assertEquals("1.1", project.getVersion());
	}

	@Test public void simpleMdeps() throws Exception {
		createFile("version", "1.1");
		createFile("mdeps", "gro.up:name:version");
		Project project = createProject();
		assertEquals("1.1", project.getVersion());
		assertThat(project.getConfigurations().getByName("implementation").getDependencies(), contains(
				allOf(hasProperty("group", is("gro.up")),
						hasProperty("name", is("name")),
						hasProperty("version", is("version"))
				)
		));
	}

	@Test public void complexMdeps() throws Exception {
		createFile("version", "1.1");
		createFile("mdeps", "group.1:name-1:version1\n"
				+ "group.2:name-2::classifier2@ext2 # configuration2\n"
		);
		Project project = createProject();
		assertEquals("1.1", project.getVersion());
		assertThat(project.getConfigurations().getByName("implementation").getDependencies(), contains(
				allOf(hasProperty("group", is("group.1")),
						hasProperty("name", is("name-1")),
						hasProperty("version", is("version1"))
				)
		));
		assertThat(project.getConfigurations().getByName("configuration2").getDependencies(), contains(
				allOf(hasProperty("group", is("group.2")),
						hasProperty("name", is("name-2")),
						hasProperty("version", is("latest.integration"))
				)
		));
	}

}
