package org.scm4j.deployer.api;

import lombok.Getter;
import lombok.Setter;

public enum DeploymentResult {
	OK, NEWER_VERSION_EXISTS, REBOOT_CONTINUE, NEED_REBOOT, INCOMPATIBLE_API_VERSION, ALREADY_INSTALLED, FAILED;
	@Getter
	@Setter
	private String productCoords;
}
