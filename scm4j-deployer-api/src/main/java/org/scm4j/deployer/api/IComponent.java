package org.scm4j.deployer.api;

import org.scm4j.commons.Coords;

public interface IComponent {
    Coords getArtifactCoords();
    IInstallationProcedure getInstallationProcedure();
}
