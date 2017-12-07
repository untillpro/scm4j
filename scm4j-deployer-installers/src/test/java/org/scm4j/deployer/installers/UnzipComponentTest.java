package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.DeploymentResult;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class UnzipComponentTest {

    private static final File ZIP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "test-zip");
    private static final File ZIP_FILE = new File(ZIP_FOLDER, "file.zip");
    private DeploymentContext depCtx;

    @Before
    public void setUp() throws Exception {
        ZIP_FOLDER.mkdirs();
        ZIP_FILE.createNewFile();
        depCtx = new DeploymentContext(ZIP_FILE.getName());
        depCtx.setDeploymentPath(ZIP_FOLDER.getPath());
        Map<String, File> artifacts = new HashMap<>();
        artifacts.put(ZIP_FILE.getName(), ZIP_FILE);
        depCtx.setArtifacts(artifacts);
        String str = "Test String";
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(ZIP_FILE));
        ZipEntry entry = new ZipEntry("mytext.txt");
        out.putNextEntry(entry);
        byte[] data = str.getBytes();
        out.write(data, 0, data.length);
        out.closeEntry();
        out.close();
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(ZIP_FOLDER);
    }

    @Test
    public void testDeployUndeploy() throws Exception {
        UnzipComponent unzipComponent = new UnzipComponent();
        File textFile = new File(ZIP_FOLDER, "mytext.txt");
        unzipComponent.init(depCtx);
        unzipComponent.deploy();
        assertEquals(FileUtils.readFileToString(textFile, "UTF-8"), "Test String");
        unzipComponent.undeploy();
        assertFalse(textFile.exists());
    }

    @Test
    public void testFail() throws Exception {
        UnzipComponent unzipComponent = new UnzipComponent();
        unzipComponent.init(depCtx);
        FileUtils.forceDelete(unzipComponent.getZipFile());
        DeploymentResult res = unzipComponent.deploy();
        assertNotEquals(DeploymentResult.OK, res);
    }

    @Test
    public void testInit() throws Exception {
        UnzipComponent unzipComponent = new UnzipComponent();
        unzipComponent.init(depCtx);
        assertEquals(unzipComponent.getZipFile(), depCtx.getArtifacts().get(ZIP_FILE.getName()));
        assertTrue(FileUtils.contentEquals(unzipComponent.getZipFile(), depCtx.getArtifacts().get(ZIP_FILE.getName())));
        assertEquals(unzipComponent.getOutputFile(), new File(depCtx.getDeploymentPath()));
    }
}
