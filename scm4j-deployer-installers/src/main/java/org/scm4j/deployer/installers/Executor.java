package org.scm4j.deployer.installers;

import lombok.Data;
import org.scm4j.deployer.api.IComponentDeployer;

import java.io.File;

@Data
public class Executor implements IComponentDeployer {
	
	private File product;
	private File outputDir;

	@Override
	public void deploy() {
		Utils.unzip(outputDir, product);
		try {
			Process p = Runtime.getRuntime().exec(product.getPath());
			p.waitFor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void undeploy() {

	}

	@Override
	public boolean validate() {
		return false;
	}
}
