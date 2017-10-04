package org.scm4j.deployer.installers;

import lombok.Data;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;


@Data
public class UnzipArtifact implements IComponentDeployer {

    private File outputFile;
    private File zipFile;

    public UnzipArtifact(File outputFile, File zipFile) {
        this.outputFile = outputFile;
        this.zipFile = zipFile;
    }

    @Override
    public void deploy() {
        Utils.unzip(outputFile,zipFile);
    }

    @Override
    public void undeploy() {

    }

    @Override
    public void init(IDeploymentContext depCtx) {

    }
}
