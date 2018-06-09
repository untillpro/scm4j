package org.scm4j.deployer.installers;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentContext;

import java.io.File;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.scm4j.deployer.installers.Common.normalize;

public class ShortcutTest {

	private static final File TEST_FOLDER = new File(System.getProperty("java.io.tmpdir"), "test-shortcut");
	private static final String TEST_FILE_NAME = "for-shortcut";
	private static final String SHORTCUT_NAME = "hello";
	private static DeploymentContext depCtx;
	private File testFile;
	private Shortcut sc;

	@BeforeClass
	public static void beforeClass() {
		depCtx = new DeploymentContext(SHORTCUT_NAME + "-0.0.0");
	}

	@AfterClass
	public static void afterClass() throws Exception {
		FileUtils.deleteDirectory(TEST_FOLDER);
	}

	@Before
	public void before() throws Exception {
		testFile = new File(TEST_FOLDER, TEST_FILE_NAME);
		FileUtils.writeStringToFile(testFile, "abc", "UTF-8");
		sc = new Shortcut();
		sc.init(depCtx);
		sc.setPathToExistingFile(normalize(testFile.getPath()));
	}

	@Test
	public void testSimpleShortcut() {
		sc.setDeploymentPath(normalize(TEST_FOLDER.getPath()));
		sc.deploy();
		assertTrue(new File(TEST_FOLDER, SHORTCUT_NAME + ".lnk").exists());
	}

	@Test
	public void testDesktopShortcut() {
		sc.deploy();
		File desktop = new File(System.getProperty("user.home") + "/Desktop");
		File shortcut = new File(desktop, SHORTCUT_NAME + ".lnk");
		assertTrue(shortcut.exists());
		FileUtils.deleteQuietly(shortcut);
	}

	@Test
	public void testNotDefaultName() {
		String testName = "abc";
		sc.setShortcutName(testName)
				.setDeploymentPath(normalize(TEST_FOLDER.getPath()))
				//image tested manually
				.setImage("C:/Windows/System32/imageres.dll");
		sc.deploy();
		assertTrue(new File(TEST_FOLDER, testName + ".lnk").exists());
	}

	@Test
	public void testUndeploy() {
		sc.setDeploymentPath(normalize(TEST_FOLDER.getPath()));
		sc.deploy();
		File shortcut = new File(TEST_FOLDER, SHORTCUT_NAME + ".lnk");
		assertTrue(shortcut.exists());
		sc.undeploy();
		assertFalse(shortcut.exists());
	}
}
