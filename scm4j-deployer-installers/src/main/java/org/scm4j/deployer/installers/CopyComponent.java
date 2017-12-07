package org.scm4j.deployer.installers;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.IOException;

import static org.scm4j.deployer.api.DeploymentResult.NEED_REBOOT;
import static org.scm4j.deployer.api.DeploymentResult.OK;

public class CopyComponent implements IComponentDeployer {

    @Getter
    private File outputFile;
    @Getter
    private File fileForDeploy;

    @Override
    public DeploymentResult deploy() {
        try {
            FileUtils.copyDirectory(fileForDeploy, outputFile);
            return OK;
        } catch (IOException e) {
            return NEED_REBOOT;
        }
    }

    @Override
    public DeploymentResult undeploy() {
        try {
            File fileForUndeploy = new File(outputFile.getPath(), fileForDeploy.getName());
            FileUtils.forceDelete(fileForUndeploy);
            return OK;
        } catch (IOException e) {
            return NEED_REBOOT;
        }
    }

    //TODO find working services who execute's from this directory and try to stop them
    @Override
    public DeploymentResult stop() {
        return OK;
    }

    //TODO how to start copied artifacts?
    @Override
    public DeploymentResult start() {
        return OK;
    }

    @Override
    public void init(IDeploymentContext depCtx) {
        outputFile = new File(depCtx.getDeploymentPath());
        fileForDeploy = depCtx.getArtifacts().get(depCtx.getMainArtifact());
    }
}
