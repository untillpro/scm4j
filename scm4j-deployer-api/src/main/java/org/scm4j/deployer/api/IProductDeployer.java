package org.scm4j.deployer.api;

import java.util.List;
import java.util.Map;

public interface IProductDeployer {

	DeploymentResult deploy(String artifactId, String version);

	void download(String artifactId, String version);

	List<String> listProducts();

	List<String> refreshProducts();

	Map<String, Boolean> listProductVersions(String artifactId);

	Map<String, Boolean> refreshProductVersions(String artifactId);

	Map<String, Boolean> listDeployedProducts(String artifactId);

}
