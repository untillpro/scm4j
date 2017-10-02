package org.scm4j.deployer.api;

import java.util.List;
import java.util.Map;

public interface IProductDeployer {

	void deploy(String productCoords);

	void undeploy(String productCoors);

	void upgrade(String newProductCoords);

	void download(String productCoords);

	List<String> listProducts();

	List<String> refreshProducts();

	Map<String, Boolean> listProductVersions(String artifactId);

	Map<String, Boolean> refreshProductVersions(String artifactId);

	List<String> listDeployedProducts();

	List<String> listDownloadedProducts();
}
