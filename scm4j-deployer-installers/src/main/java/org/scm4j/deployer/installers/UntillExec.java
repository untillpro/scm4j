package org.scm4j.deployer.installers;

import lombok.extern.slf4j.Slf4j;
import org.scm4j.deployer.api.DeploymentResult;

@Slf4j
public class UntillExec extends Exec {

	@Override
	public DeploymentResult deploy() {
		DeploymentResult res = super.deploy();
		if (res == DeploymentResult.FAILED) {
			return DeploymentResult.REBOOT_CONTINUE;
		}
		return res;
	}

	@Override
	public DeploymentResult undeploy() {
		DeploymentResult res = super.undeploy();
		log.info("untill undeploy returns " + res.toString());
		return DeploymentResult.OK;
	}

	@Override
	public String toString() {
		return "UntillExec{product=" + super.getMainArtifact() + '}';
	}
}
