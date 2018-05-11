package org.scm4j.deployer.installers;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.util.concurrent.Callable;

@Accessors(chain = true)
public class Check implements IComponentDeployer {

	@Setter
	private Callable<Boolean> deployCondition;
	@Setter
	private Callable<Boolean> undeployCondition;
	@Setter
	private Callable<Boolean> stopCondition;
	@Setter
	private Callable<Boolean> startCondition;

	@Override
	@SneakyThrows
	public DeploymentResult deploy() {
		if (deployCondition.call())
			return DeploymentResult.OK;
		else
			return DeploymentResult.FAILED;
	}

	@Override
	@SneakyThrows
	public DeploymentResult undeploy() {
		if (undeployCondition.call())
			return DeploymentResult.OK;
		else
			return DeploymentResult.FAILED;
	}

	@Override
	@SneakyThrows
	public DeploymentResult stop() {
		if (stopCondition.call())
			return DeploymentResult.OK;
		else
			return DeploymentResult.FAILED;
	}

	@Override
	@SneakyThrows
	public DeploymentResult start() {
		if (startCondition.call())
			return DeploymentResult.OK;
		else
			return DeploymentResult.FAILED;
	}

	@Override
	public void init(IDeploymentContext depCtx) {
	}

}
