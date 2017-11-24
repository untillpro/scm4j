package org.scm4j.deployer.api;

import java.io.File;

public interface ILegacyProduct {

    String getLegacyVersion();

    File getLegacyFile();

    DeploymentResult removeLegacyProduct();

    boolean queryLegacyProduct();

}
