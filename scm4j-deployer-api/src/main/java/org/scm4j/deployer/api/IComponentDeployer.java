package org.scm4j.deployer.api;

public interface IComponentDeployer {

	void deploy();

	void undeploy();

	boolean validate();

}
