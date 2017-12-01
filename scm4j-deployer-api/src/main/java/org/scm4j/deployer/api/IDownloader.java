package org.scm4j.deployer.api;

import java.io.File;
import java.util.Map;

public interface IDownloader {

    Map<String, ? extends IDeploymentContext> getDepCtx();

    File getProductFile();

}
