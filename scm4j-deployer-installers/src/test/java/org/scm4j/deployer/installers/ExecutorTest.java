package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.installers.exception.EInstallationException;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ExecutorTest {

    private static final File TMP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
    private static final String MAIN_ARTIFACT = "unTill";
    private DeploymentContext depCtx;
    private Map<String, Object> params;

    @Before
    public void setUp() throws Exception {
        TMP_FOLDER.mkdirs();
        depCtx = new DeploymentContext(MAIN_ARTIFACT);
        depCtx.setDeploymentURL(new URL("file://C:/unTill"));
        params = new HashMap<>();
        Map<String, Map<String, Object>> mainParams = new HashMap<>();
        String param = " /silent /prepare_restart=1 /dir=\"C:/unTill\" /log=\"C:/unTill/silentsetup.txt\"";
        params.put("deploy", param);
        mainParams.put("Executor", params);
        depCtx.setParams(mainParams);
        Map<String, File> artifacts = new HashMap<>();
        File mainArtifactFolder = new File(TMP_FOLDER, MAIN_ARTIFACT + ".exe");
        mainArtifactFolder.createNewFile();
        artifacts.put(MAIN_ARTIFACT, mainArtifactFolder);
        depCtx.setArtifacts(artifacts);
    }

    @After
    public void tearDown() throws Exception {
        depCtx = null;
        FileUtils.deleteDirectory(TMP_FOLDER);
    }

    @Ignore
    public void testDeploy() throws Exception {
        Executor executor = new Executor();
        executor.init(depCtx, params);
        try {
            executor.deploy();
            fail();
        } catch (EInstallationException e) {
        }
    }

    @Test
    public void testInit() throws Exception {
        Executor exec = new Executor();
        exec.init(depCtx, params);
        assertEquals(exec.getParams(), depCtx.getParams().get(exec.getClass().getSimpleName()));
        assertEquals(exec.getProduct(), depCtx.getArtifacts().get(MAIN_ARTIFACT));
        assertTrue(FileUtils.contentEquals(exec.getProduct(), depCtx.getArtifacts().get(MAIN_ARTIFACT)));
        assertEquals(exec.getOutputDir(), new File(depCtx.getDeploymentURL().getFile()));
    }

}
