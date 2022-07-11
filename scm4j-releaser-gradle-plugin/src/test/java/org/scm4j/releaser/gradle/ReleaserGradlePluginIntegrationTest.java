package org.scm4j.releaser.gradle;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

@Category(IntegrationTest.class)
public class ReleaserGradlePluginIntegrationTest {

	@Rule public TemporaryFolder testProjectDir = new TemporaryFolder();

	private void createFile(String fileName, String fileContent) throws IOException {
		File file = testProjectDir.newFile(fileName);
		Files.write(file.toPath(), fileContent.getBytes());
	}

	@Test public void noVersionFile() throws Exception {
		createFile("build.gradle", "plugins { id 'org.scm4j.releaser.scm4j-releaser-gradle-plugin' }");
		Exception e = assertThrows(UnexpectedBuildFailure.class, () -> {
			GradleRunner.create()
					.withProjectDir(testProjectDir.getRoot())
					.withPluginClasspath()
					.build();
		});
		assertThat(e.getMessage(), containsString("version file is not found"));
	}

	@Test public void version() throws Exception {
		createFile("build.gradle", "plugins { id 'org.scm4j.releaser.scm4j-releaser-gradle-plugin' }\n"
				+ "task testVersion { doLast { assert version == '1.1' } }");
		createFile("version", "1.1");
		BuildResult result = GradleRunner.create()
				.withProjectDir(testProjectDir.getRoot())
				.withPluginClasspath()
				.withArguments("testVersion")
				.build();
		assertEquals(TaskOutcome.SUCCESS, requireNonNull(result.task(":testVersion")).getOutcome());
	}

}
