package org.scm4j.deployer.api;

import java.io.File;

public interface IDownloader {

    <T extends IDeploymentContext> T getContextByArtifactId(String artifactId);

    File getProductFile(String coords) throws Exception;

    IProduct getProduct();

}
