package org.scm4j.releaser.gradle;

import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class ReleaserGradlePluginTest {

	@Rule public TemporaryFolder testProjectDir = new TemporaryFolder();
	@Rule public ExpectedException thrown = ExpectedException.none();

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

	@Test public void noVersionFile() throws Exception {
		thrown.expect(PluginApplicationException.class);
		thrown.expectCause(isA(GradleException.class));
		thrown.expectCause(hasProperty("message", is("version file is not found")));
		createProject();
	}

	@Test public void version() throws Exception {
		createFile("version", "1.1");
		Project project = createProject();
		assertEquals("1.1", project.getVersion());
	}

	@Test public void simpleMdeps() throws Exception {
		createFile("version", "1.1");
		createFile("mdeps", "g.roup:name:version");
		Project project = createProject();
		assertEquals("1.1", project.getVersion());
		assertThat(project.getConfigurations().getByName("compile").getDependencies(), contains(
				allOf(hasProperty("group", is("g.roup")),
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
		assertThat(project.getConfigurations().getByName("compile").getDependencies(), contains(
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
