package org.scm4j.deployer.api;

import java.util.Map;

public interface IComponentDeployer {

	int deploy();

	int undeploy();

	void init(IDeploymentContext depCtx, Map<String,Object> params);

}
