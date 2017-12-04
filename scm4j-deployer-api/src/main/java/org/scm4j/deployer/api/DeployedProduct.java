package org.scm4j.deployer.api;

import lombok.Data;

@Data
public class DeployedProduct implements IDeployedProduct {

    private String deploymentPath;
    private String productVersion;
    private IProductStructure productStructure;

}
