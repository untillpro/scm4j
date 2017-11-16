package org.scm4j.deployer.api;

import lombok.Getter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.util.ArrayList;

public class Component implements IComponent {

    @Getter
    final private Artifact artifactCoords;
    @Getter
    private IDeploymentProcedure deploymentProcedure;
    private final ProductStructure ps;

    public Component(String coords, ProductStructure productStructure) {
        this.artifactCoords = new DefaultArtifact(coords);
        this.ps = productStructure;
    }

    public Component addComponentDeployer(IComponentDeployer deployer) {
        if (this.deploymentProcedure == null)
            this.deploymentProcedure = new DeploymentProcedure(new ArrayList<>());
        this.deploymentProcedure.getComponentDeployers().add(deployer);
        return this;
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
