package org.scm4j.deployer.api;

import java.net.URL;

public interface IDeployedProduct extends IProduct {

    URL getDeploymentUrl();
    String getProductVersion();

}
