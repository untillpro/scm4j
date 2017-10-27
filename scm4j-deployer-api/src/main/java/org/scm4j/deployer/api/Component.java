package org.scm4j.deployer.api;

import lombok.Getter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.util.ArrayList;

public class Component implements IComponent {

    @Getter final private Artifact artifactCoords;
    @Getter private IDeploymentProcedure installationProcedure;
    private final ProductStructure ps;

    public Component(String coords, ProductStructure productStructure) {
        this.artifactCoords = new DefaultArtifact(coords);
        this.ps = productStructure;
    }

    public Action addAction(String clazz) {
        Action action = new Action(clazz, this);
            if (this.installationProcedure == null) {
                this.installationProcedure = new DeploymentProcedure(new ArrayList<>());
                this.installationProcedure.getActions().add(action);
            } else {
                this.installationProcedure.getActions().add(action);
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
