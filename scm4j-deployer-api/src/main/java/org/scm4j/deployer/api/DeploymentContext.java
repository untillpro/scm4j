package org.scm4j.deployer.api;

import lombok.Data;

import java.io.File;
import java.net.URL;
import java.util.Map;

@Data
public class DeploymentContext implements IDeploymentContext {

    private String mainArtifact;
    private Map<String, File> artifacts;
    private URL deploymentURL;
    private Map<Class, Map<String,Object>> params;

    public DeploymentContext(String mainArtifact) {
        this.mainArtifact = mainArtifact;
    }
}
