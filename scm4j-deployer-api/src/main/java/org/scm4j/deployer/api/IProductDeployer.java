package org.scm4j.deployer.api;

import java.util.Map;

public interface IProductDeployer {

	DeploymentResult deploy(String artifactId, String version);

	void download(String artifactId, String version);

	Map<String, String> mapProductNamesAndArtifactIds();

	Map<String, String> refreshProductNamesAndArtifactIds();

	Map<String, Boolean> listProductVersions(String artifactId);

	Map<String, Boolean> refreshProductVersions(String artifactId);

	Map<String, Object> listDeployedProducts();

}
