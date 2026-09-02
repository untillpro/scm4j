package org.scm4j.deployer.installers;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;

@Accessors(chain = true)
public class Delete implements IComponentDeployer {

	@Setter
	private String fileToDeleteOnDeploy;
	@Setter
	private String fileToDeleteOnUndeploy;
	@Setter
	private String fileToDeleteOnStop;
	@Setter
	private String fileToDeleteOnStart;

	private String deploymentPath;

	private DeploymentResult deleteFile(String fileToDelete) {
		if (fileToDelete == null)
			return DeploymentResult.OK;
		fileToDelete = StringUtils.replace(fileToDelete, "$deploymentPath", deploymentPath);
		File file = new File(fileToDelete);
		if (!file.exists())
			return DeploymentResult.OK;
		if (file.delete()) {
			return DeploymentResult.OK;
		} else {
			DeploymentResult dr = DeploymentResult.FAILED;
			dr.setErrorMsg("File doesn't deleted!");
			return dr;
		}
	}

	@Override
	public DeploymentResult deploy() {
		return deleteFile(fileToDeleteOnDeploy);
	}

	@Override
	public DeploymentResult undeploy() {
		return deleteFile(fileToDeleteOnUndeploy);
	}

	@Override
	public DeploymentResult stop() {
		return deleteFile(fileToDeleteOnStop);
	}

	@Override
	public DeploymentResult start() {
		return deleteFile(fileToDeleteOnStart);
	}

	@Override
	public void init(IDeploymentContext depCtx) {
		deploymentPath = depCtx.getDeploymentPath();
	}

}
