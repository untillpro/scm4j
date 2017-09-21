package org.scm4j.deployer.installers;

public interface IDeployer {

	void deploy();

	void unDeploy();

	boolean canDeploy();

	boolean checkIntegrity();

}
