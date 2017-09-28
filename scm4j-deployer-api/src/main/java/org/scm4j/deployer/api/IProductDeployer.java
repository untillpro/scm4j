package org.scm4j.deployer.api;

import org.scm4j.commons.Version;

import java.util.List;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

	List<String> listProducts();

	List<String> refreshProducts();

	List<Version> listProductVersions(String artifactId);

	List<Version> refreshProductVersions(String artifactId);

	List<String> listDeployedProducts();

}
