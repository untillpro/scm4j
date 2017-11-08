package org.scm4j.deployer.api;

import java.util.Map;

public interface IComponentDeployer {

	int deploy();

	int undeploy();

	/**
	 * Trying to stop component, if can't then change startup type to disabled
	 *
	 * @return 0 if successful, other int if don't
	 */
	int stop();

	/**
	 * Start component after upgrade and change startup type to automatic
	 *
	 * @return 0 if successful, other int if don't
	 */
	int start();

	void init(IDeploymentContext depCtx, Map<String,Object> params);

}
