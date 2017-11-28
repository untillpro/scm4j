package org.scm4j.deployer.api;

import lombok.Data;

@Data
public class DeployedProduct implements IProduct, IDeployedProduct {

    private String productFileName;
    private String productVersion;
    private IProductStructure productStructure;

}
