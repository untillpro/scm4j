package org.scm4j.deployer.api;

import lombok.Data;

import java.io.File;
import java.util.Map;

@Data
public class DeploymentContext implements IDeploymentContext {

	private final String mainArtifact;
	private Map<String, File> artifacts;
	private String deploymentPath;

	public DeploymentContext(String mainArtifact) {
		this.mainArtifact = mainArtifact;
	}

	@Override
	public String toString() {
		return "DeploymentContext{" +
				"mainArtifact='" + mainArtifact + '\'' +
				'}';
	}
}
