package org.scm4j.deployer.api;

import lombok.Getter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.util.ArrayList;

public class Component implements IComponent {

    @Getter final private Artifact artifactCoords;
    @Getter private IDeploymentProcedure deploymentProcedure;
    private final ProductStructure ps;

    public Component(String coords, ProductStructure productStructure) {
        this.artifactCoords = new DefaultArtifact(coords);
        this.ps = productStructure;
    }

    public Action addAction(Class<? extends IComponentDeployer> clazz) {
        Action action = new Action(clazz, this);
            if (this.deploymentProcedure == null) {
                this.deploymentProcedure = new DeploymentProcedure(new ArrayList<>());
                this.deploymentProcedure.getActions().add(action);
            } else {
                this.deploymentProcedure.getActions().add(action);
            }
            return action;
    }

    public ProductStructure parent() {
        return ps;
    }

    @Override
    public String toString() {
        return "Component{" +
                "artifactCoords=" + artifactCoords +
                '}';
    }
}
