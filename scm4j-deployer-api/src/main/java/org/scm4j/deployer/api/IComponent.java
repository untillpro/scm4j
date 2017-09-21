package org.scm4j.deployer.api;

public interface IComponent {
    String getArtifactCoords();
    IInstallationProcedure getInstallationProcedure();
}
