package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExecOldTest {

	private static final File TMP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
	private static final String MAIN_ARTIFACT = "unTill";
	private DeploymentContext depCtx;
	private ExecOld executor;
	private File mainArtifactFolder;

	@Before
	public void setUp() throws Exception {
		TMP_FOLDER.mkdirs();
		depCtx = new DeploymentContext(MAIN_ARTIFACT);
		depCtx.setDeploymentPath("C:/Program Files/unTill");
		String deployCmd = " /silent /prepare_restart=1 /dir=$deploymentPath";
		String undeployCmd = " /verysilent";
		Map<String, File> artifacts = new HashMap<>();
		mainArtifactFolder = new File(TMP_FOLDER, MAIN_ARTIFACT + ".exe");
		mainArtifactFolder.createNewFile();
		artifacts.put(MAIN_ARTIFACT, mainArtifactFolder);
		depCtx.setArtifacts(artifacts);
		executor = new ExecOld();
		executor.init(depCtx);
		executor.setDeployCmd(deployCmd);
		executor.setUndeployCmd(undeployCmd);
		executor.setUndeployExecutableName("unins000.exe");
		executor.setStopExecutableName("untill.exe");
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(TMP_FOLDER);
	}

	@Test
	public void testCreateCmd() {
		ProcessBuilder expected = executor.getBuilder(executor.getDeployCmd());
		ProcessBuilder actual = new ProcessBuilder(StringUtils
				.replace(mainArtifactFolder.getPath(), "\\", "/"), "/silent",
				"/prepare_restart=1", "/dir=C:/Program Files/unTill");
		assertEquals(expected.command(), actual.command());
	}

	@Test
	public void testInit() throws Exception {
		assertEquals(executor.getDefaultExecutable(), depCtx.getArtifacts().get(MAIN_ARTIFACT));
		assertTrue(FileUtils.contentEquals(executor.getDefaultExecutable(), depCtx.getArtifacts().get(MAIN_ARTIFACT)));
		assertEquals(executor.getOutputDir(), new File(depCtx.getDeploymentPath()));
	}

	@Test
	public void testInitEmptyAtrifacts() {
		depCtx = new DeploymentContext(MAIN_ARTIFACT);
		depCtx.setDeploymentPath("C:/");
		executor.init(depCtx);
		assertNull(depCtx.getArtifacts());
	}
}
