package org.scm4j.deployer.installers.inv;

import org.junit.Ignore;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.installers.UntillExec;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class UntillExecTest {

	private static final File UNTILL_SETUP = new File("C:\\ProgramData\\unTill\\installer\\repository"
			+ "\\eu\\untill\\unTillSetup\\127.0\\unTillSetup-127.0.exe");

	@Ignore
	public void execTest() {
		DeploymentContext depCtx = new DeploymentContext(UNTILL_SETUP.getName());
		Map<String, File> artifacts = new HashMap<>();
		artifacts.put(UNTILL_SETUP.getName(), UNTILL_SETUP);
		depCtx.setArtifacts(artifacts);
		depCtx.setDeploymentPath("C:/unTill");
		UntillExec exec = new UntillExec();
		exec.init(depCtx);
		exec.setStatusFileName("C:/tools/ssf.txt")
				.setNeedRebootExitValue(33)
				.setArgs("/verysilent", "/prepare_restart=1", "/norestart", "/restartexitcode=$restartexitcode", "/doNotForceRestart");
		DeploymentResult res = exec.deploy();
		System.out.println(res.toString());
	}
}
