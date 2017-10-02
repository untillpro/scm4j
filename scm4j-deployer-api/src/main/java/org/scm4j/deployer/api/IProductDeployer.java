package org.scm4j.deployer.api;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

	File download(String productCoords);

	List<String> listProducts();

	List<String> refreshProducts();

	Map<String, Boolean> listProductVersions(String artifactId);

	Map<String, Boolean> refreshProductVersions(String artifactId);

	List<String> listDeployedProducts();
}
