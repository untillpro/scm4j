package org.scm4j.deployer.api;

import org.scm4j.commons.Version;

import java.io.File;

public interface ILegacyProduct {

    Version getLegacyVersion();

    File getLegacyFile();

    DeploymentResult removeLegacyProduct();

    boolean queryLegacyProduct();

}
