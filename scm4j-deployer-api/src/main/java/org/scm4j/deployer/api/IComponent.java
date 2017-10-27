package org.scm4j.deployer.api;

import org.eclipse.aether.artifact.Artifact;

public interface IComponent {
    Artifact getArtifactCoords();
    IDeploymentProcedure getInstallationProcedure();
}
