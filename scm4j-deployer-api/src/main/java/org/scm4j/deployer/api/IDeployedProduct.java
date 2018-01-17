package org.scm4j.deployer.api;

public interface IDeployedProduct extends IProduct {

	String getDeploymentPath();

	String getProductVersion();

}
