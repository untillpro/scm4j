package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.DeploymentResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.scm4j.deployer.api.DeploymentResult.NEED_REBOOT;

public class CopyTest {

	private static final File TEST_FOLDER = new File(System.getProperty("java.io.tmpdir"), "test-copy");
	private static final File FOLDER_FOR_COPY = new File(TEST_FOLDER, "files");
	private static final File FILE_FOR_COPY = new File(TEST_FOLDER, "file.txt");
	private static final File OUTPUT_FOLDER = new File(TEST_FOLDER, "output");
	private DeploymentContext depCtx;

	@BeforeClass
	public static void setUp() throws Exception {
		FOLDER_FOR_COPY.mkdirs();
		FILE_FOR_COPY.createNewFile();
		FileUtils.writeStringToFile(FILE_FOR_COPY, "hello file", "UTF-8");
		for (int i = 0; i < 5; i++) {
			File file = new File(FOLDER_FOR_COPY, String.valueOf(i));
			file.mkdir();
			File file1 = new File(file, String.valueOf(i) + ".txt");
			file1.createNewFile();
			FileUtils.writeStringToFile(file1, "hello" + String.valueOf(i), "UTF-8");
		}
	}

	@AfterClass
	public static void tearDown() throws Exception {
		FileUtils.deleteDirectory(TEST_FOLDER);
	}

	@Before
	public void before() {
		depCtx = new DeploymentContext(FOLDER_FOR_COPY.getName());
		depCtx.setDeploymentPath(OUTPUT_FOLDER.getPath());
		Map<String, File> artifacts = new HashMap<>();
		artifacts.put(FOLDER_FOR_COPY.getName(), FOLDER_FOR_COPY);
		depCtx.setArtifacts(artifacts);
	}

	@Test
	public void testDeployFolder() throws Exception {
		Copy copy = new Copy();
		copy.init(depCtx);
		copy.deploy();
		File newFile;
		for (int i = 0; i < 5; i++) {
			newFile = new File(OUTPUT_FOLDER, FOLDER_FOR_COPY.getName());
			newFile = new File(newFile, String.valueOf(i));
			newFile = new File(newFile, String.valueOf(i) + ".txt");
			File ethalon = new File(FOLDER_FOR_COPY, String.valueOf(i));
			assertTrue(newFile.exists());
			assertTrue(FileUtils.contentEquals(new File(ethalon, String.valueOf(i) + ".txt"),
					newFile));
		}
	}

	@Test
	public void testDeployFile() throws Exception {
		Map<String, File> artifacts = new HashMap<>();
		artifacts.put(FILE_FOR_COPY.getName(), FILE_FOR_COPY);
		depCtx.setArtifacts(artifacts);
		Copy copy = new Copy();
		copy.init(depCtx);
		copy.deploy();
		assertEquals(FileUtils.readFileToString(FILE_FOR_COPY, "UTF-8"), "hello file");
	}

	@Test
	public void testFail() throws Exception {
		Copy copy = new Copy();
		copy.init(depCtx);
		for (File file : copy.getFilesForDeploy()) {
			FileUtils.forceDelete(file);
		}
		DeploymentResult res = copy.deploy();
		assertEquals(NEED_REBOOT, res);

		setUp();
	}

	@Test
	public void testInit() {
		Copy copy = new Copy();
		copy.init(depCtx);
		assertEquals(copy.getFilesForDeploy(), depCtx.getArtifacts().values());
		assertEquals(copy.getOutputFile(), new File(depCtx.getDeploymentPath()));
	}

	@Test
	public void testInitSetPath() {
		Copy copy = new Copy();
		copy.setDefaultFolderName("hello");
		copy.init(depCtx);
		assertEquals(new File(OUTPUT_FOLDER, "hello"), copy.getOutputFile());
	}

}
