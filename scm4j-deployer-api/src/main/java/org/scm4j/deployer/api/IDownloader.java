package org.scm4j.deployer.api;

import java.io.File;
import java.util.Map;

public interface IDownloader {

    Map<String, ? super IDeploymentContext> getDepCtx();

    File getProductFile(String coords) throws Exception;

    IProduct getProduct();

}
