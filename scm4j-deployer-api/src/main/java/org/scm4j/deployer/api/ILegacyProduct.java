package org.scm4j.deployer.api;

/**
 * The interface Legacy product.
 */
public interface ILegacyProduct {


    /**
     * Query legacy deployed product.
     *
     * @return the deployed product or null if not found
     */
    <T extends IDeployedProduct> T queryLegacyDeployedProduct();

}
