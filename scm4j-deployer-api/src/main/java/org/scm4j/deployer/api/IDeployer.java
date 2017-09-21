package org.scm4j.deployer.api;

public interface IDeployer {

	void deploy();

	void unDeploy();

	boolean canDeploy();

	boolean checkIntegrity();

}
