package org.scm4j.deployer.installers;

public interface IAI {

	void install(String productCoords);

	void uninstall(String productCoors);

	void upgrade(String newProductCoords);

}
