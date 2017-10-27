package org.scm4j.deployer.api;

import java.util.Map;

public interface IComponentDeployer {

	void deploy();

	void undeploy();

	void init(IDeploymentContext depCtx, Map<String,Object> params);

}
