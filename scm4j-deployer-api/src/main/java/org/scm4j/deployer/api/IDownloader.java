package org.scm4j.deployer.api;

import org.scm4j.deployer.api.exceptions.EIncompatibleApiVersion;

import java.io.File;
import java.util.Map;

public interface IDownloader {

    Map<String, ? extends IDeploymentContext> getDepCtx();

    File getProductFile(String coords) throws EIncompatibleApiVersion;

    IProduct getProduct();

}
