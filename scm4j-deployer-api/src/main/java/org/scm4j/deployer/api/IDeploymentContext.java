package org.scm4j.deployer.api;

import org.scm4j.commons.Coords;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

public interface IDeploymentContext {

    String getMainArtifact();
    Map<Coords, File> getArtifacts();
    URL getDeploymentURL();
    Map<String, Object> getParams();

}
