package org.scm4j.deployer.api;

import java.util.Map;

public interface IProductDeployer {

	DeploymentResult deploy(String simpleName, String version);

	void download(String simpleName, String version);

	Map<String, ProductInfo> listProducts();

	Map<String, ProductInfo> refreshProducts();

	Map<String, Boolean> listProductVersions(String simpleName);

	Map<String, Boolean> refreshProductVersions(String simpleName);

	Map<String, String> mapDeployedProducts();

}
