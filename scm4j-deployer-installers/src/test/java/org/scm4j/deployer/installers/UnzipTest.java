package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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

public class UnzipTest {

    private static final File ZIP_FOLDER = new File(System.getProperty("java.io.tmpdir"), "test-zip");
    private static final File ZIP_FILE = new File(ZIP_FOLDER, "file.zip");
    private static final String ZIPPED_FOLDER_NAME = "zipped-folder/";
    private DeploymentContext depCtx;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ZIP_FOLDER.mkdirs();
        ZIP_FILE.createNewFile();
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(ZIP_FILE));
        ZipEntry entry;
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0)
                entry = new ZipEntry(ZIPPED_FOLDER_NAME + "mytext" + i + ".txt");
            else
                entry = new ZipEntry("mytext" + i + ".txt");
            String str = "Test String" + i;
            out.putNextEntry(entry);
            byte[] data = str.getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        out.close();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        FileUtils.deleteDirectory(ZIP_FOLDER);
    }

    @Before
    public void before() {
        depCtx = new DeploymentContext(ZIP_FILE.getName());
        depCtx.setDeploymentPath(ZIP_FOLDER.getPath());
        Map<String, File> artifacts = new HashMap<>();
        artifacts.put(ZIP_FILE.getName(), ZIP_FILE);
        depCtx.setArtifacts(artifacts);
    }

    @Test
    public void testDeployUndeploy() throws Exception {
        Unzip unzip = new Unzip();
        unzip.init(depCtx);

        unzip.deploy();
        File testFile;
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0)
                testFile = new File(ZIP_FOLDER, ZIPPED_FOLDER_NAME + "mytext" + i + ".txt");
            else
                testFile = new File(ZIP_FOLDER, "mytext" + i + ".txt");
            assertEquals(FileUtils.readFileToString(testFile, "UTF-8"), "Test String" + i);
        }

        unzip.undeploy();
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0)
                testFile = new File(ZIP_FOLDER, ZIPPED_FOLDER_NAME + "mytext" + i + ".txt");
            else
                testFile = new File(ZIP_FOLDER, "mytext" + i + ".txt");
            assertFalse(testFile.exists());
        }
    }

    @Test
    public void testFail() throws Exception {
        Unzip unzip = new Unzip();
        unzip.init(depCtx);

        FileUtils.forceDelete(unzip.getZipFileName());
        DeploymentResult res = unzip.deploy();
        assertNotEquals(DeploymentResult.OK, res);

        beforeClass();
    }

    @Test
    public void testNotZippedDirectory() {
        Unzip unzip = new Unzip();
        Map<String, File> arts = new HashMap<>();
        arts.put(ZIP_FILE.getName(), new File("C:/"));
        depCtx.setArtifacts(arts);
        try {
            unzip.init(depCtx);
            fail();
        } catch (RuntimeException e) {
        }
    }

    @Test
    public void testInit() throws Exception {
        Unzip unzip = new Unzip();
        unzip.init(depCtx);
        assertEquals(unzip.getZipFileName(), depCtx.getArtifacts().get(ZIP_FILE.getName()));
        assertTrue(FileUtils.contentEquals(unzip.getZipFileName(), depCtx.getArtifacts().get(ZIP_FILE.getName())));
        assertEquals(unzip.getOutputDir(), new File(depCtx.getDeploymentPath()));
    }
}
