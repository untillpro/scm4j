package org.scm4j.deployer.installers;

import java.io.File;

public class InstallerFactory {
	
	public IDeployer getInstaller(File product) {
		return new ExeRunner(product);
	}

}
