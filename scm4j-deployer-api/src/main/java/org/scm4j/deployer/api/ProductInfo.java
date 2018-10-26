package org.scm4j.deployer.api;

import lombok.Data;

@Data
public class ProductInfo {
	private final String artifactId;
	private final boolean hidden;
}
