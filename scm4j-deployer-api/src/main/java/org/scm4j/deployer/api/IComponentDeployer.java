package org.scm4j.deployer.api;

import java.util.Map;

public interface IComponentDeployer {

	DeploymentResult deploy();

	DeploymentResult undeploy();

	/**
	 * Trying to stop component, if can't then change startup type to disabled
	 *
	 * @return DeploymentResult.OK successful, DeploymentResult.NEED_REBOOT need restart
	 */
	DeploymentResult stop();

	/**
	 * Start component after upgrade and change startup type to automatic
	 *
	 * @return DeploymentResult.OK successful
	 */
	DeploymentResult start();

	void init(IDeploymentContext depCtx, Map<String,Object> params);

}
