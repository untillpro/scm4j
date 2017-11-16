package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExeRunnerTest {

    private static final File TMP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
    private static final String MAIN_ARTIFACT = "unTill";
    private DeploymentContext depCtx;
    private ExeRunner executor;

    @Before
    public void setUp() throws Exception {
        TMP_FOLDER.mkdirs();
        depCtx = new DeploymentContext(MAIN_ARTIFACT);
        depCtx.setDeploymentURL(new URL("file://C:/Program Files/unTill"));
        String deployCmd = " /silent /prepare_restart=1 /dir=$deploymentPath";
        String undeployCmd = " /verysilent";
        Map<String, File> artifacts = new HashMap<>();
        File mainArtifactFolder = new File(TMP_FOLDER, MAIN_ARTIFACT + ".exe");
        mainArtifactFolder.createNewFile();
        artifacts.put(MAIN_ARTIFACT, mainArtifactFolder);
        depCtx.setArtifacts(artifacts);
        executor = new ExeRunner();
        executor.init(depCtx);
        executor.setDeployCmd(deployCmd);
        executor.setUndeployCmd(undeployCmd);
        executor.setUndeployExecutableName("unins000.exe");
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(TMP_FOLDER);
    }

    @Test
    public void testCreateCmd() throws Exception {
        ProcessBuilder expected = executor.getBuilder(executor.getDeployCmd());
        ProcessBuilder actual = new ProcessBuilder("cmd", "/c", MAIN_ARTIFACT + ".exe", "/silent",
                "/prepare_restart=1", "/dir=/Program Files/unTill");
        ProcessBuilder undeployCmd = executor.getBuilder(executor.getUndeployCmd(), executor.getUndeployExecutable());
        ProcessBuilder undeployBuilder = new ProcessBuilder("cmd", "/c", "unins000.exe", "/verysilent");
        assertEquals(TMP_FOLDER, expected.directory());
        assertEquals(expected.command(), actual.command());
        assertEquals(executor.getUndeployExecutable().getParentFile(), executor.getOutputDir());
        assertEquals(undeployCmd.command(), undeployBuilder.command());
    }

    @Test
    public void testInit() throws Exception {
        assertEquals(executor.getDefaultExecutable(), depCtx.getArtifacts().get(MAIN_ARTIFACT));
        assertTrue(FileUtils.contentEquals(executor.getDefaultExecutable(), depCtx.getArtifacts().get(MAIN_ARTIFACT)));
        assertEquals(executor.getOutputDir(), new File(depCtx.getDeploymentURL().getFile()));
    }
}
