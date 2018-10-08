package org.scm4j.deployer.api;

import java.util.List;
import java.util.Map;

public interface IProductDeployer {

	DeploymentResult deploy(String simpleName, String version);

	void download(String simpleName, String version);

	List<String> listProducts();

	List<String> refreshProducts();

	List<String> listProductVersions(String simpleName);

	List<String> refreshProductVersions(String simpleName);

	Map<String, String> mapDeployedProducts(String simpleName);

}
