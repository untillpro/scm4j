package org.scm4j.deployer.api;

import java.io.File;

public interface IDownloader {

    <T extends IDeploymentContext> T getContextByArtifactId(String artifactId);

    File getProductFile(String coords) throws Exception;

    File getProductWithDependency(String coords) throws Exception;

    File getProductDependency(File repository);

    IProduct getProduct();

}
