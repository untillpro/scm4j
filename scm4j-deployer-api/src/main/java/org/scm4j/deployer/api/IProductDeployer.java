package org.scm4j.deployer.api;

import java.util.List;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

	List<String> listAvailableProducts();

	List<String> listAvailableProductVersions(String artifactId);

	List<String> listInstalledProducts();

}
