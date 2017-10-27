package org.scm4j.deployer.api;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IProductDeployer {

	void deploy(String artifactId, String version);

	void undeploy(String artifactId, String version);

	void upgrade(String artifactId, String version);

	File download(String artifactId, String version);

	List<String> listProducts();

	List<String> refreshProducts();

	Map<String, Boolean> listProductVersions(String artifactId);

	Map<String, Boolean> refreshProductVersions(String artifactId);

	Map listDeployedProducts();
}
