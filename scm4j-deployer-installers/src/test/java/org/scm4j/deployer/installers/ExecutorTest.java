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

public class ExecutorTest {

    private static final File TMP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
    private static final String MAIN_ARTIFACT = "unTill";
    private DeploymentContext depCtx;
    private Map<String, Object> params;
    private Executor executor;

    @Before
    public void setUp() throws Exception {
        TMP_FOLDER.mkdirs();
        depCtx = new DeploymentContext(MAIN_ARTIFACT);
        depCtx.setDeploymentURL(new URL("file://C:/Program Files/unTill"));
        params = new HashMap<>();
        Map<String, Map<String, Object>> mainParams = new HashMap<>();
        String param = " /silent /prepare_restart=1 /dir=$deploymentPath";
        params.put("deploy", param);
        mainParams.put("org.scm4j.deployer.installers.Executor", params);
        depCtx.setParams(mainParams);
        Map<String, File> artifacts = new HashMap<>();
        File mainArtifactFolder = new File(TMP_FOLDER, MAIN_ARTIFACT + ".exe");
        mainArtifactFolder.createNewFile();
        artifacts.put(MAIN_ARTIFACT, mainArtifactFolder);
        depCtx.setArtifacts(artifacts);
        executor = new Executor();
        executor.init(depCtx, params);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(TMP_FOLDER);
    }

    @Test
    public void testCreateCmd() throws Exception {
        ProcessBuilder expected = executor.getCmdToProcessBuilder().apply("deploy");
        ProcessBuilder actual = new ProcessBuilder("cmd", "/c", MAIN_ARTIFACT + ".exe", "/silent",
                "/prepare_restart=1", "/dir=/Program Files/unTill");
        assertEquals(expected.command(), actual.command());
    }

    @Test
    public void testInit() throws Exception {
        assertEquals(executor.getParams(), params);
        assertEquals(executor.getProduct(), depCtx.getArtifacts().get(MAIN_ARTIFACT));
        assertTrue(FileUtils.contentEquals(executor.getProduct(), depCtx.getArtifacts().get(MAIN_ARTIFACT)));
        assertEquals(executor.getOutputDir(), new File(depCtx.getDeploymentURL().getFile()));
    }
}
