package org.scm4j.deployer.api;

import java.util.List;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

	List<String> listProducts();

	List<String> refreshProducts();

	List<String> listProductVersions(String artifactId);

	List<String> refreshProductVersions(String artifactId);

	List<String> listDeployedProducts();

}
