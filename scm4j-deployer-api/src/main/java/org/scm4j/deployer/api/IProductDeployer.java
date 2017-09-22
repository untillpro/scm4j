package org.scm4j.deployer.api;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

}
