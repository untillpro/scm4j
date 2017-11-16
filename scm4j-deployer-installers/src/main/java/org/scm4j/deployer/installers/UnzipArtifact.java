package org.scm4j.deployer.installers;

import lombok.Cleanup;
import lombok.Data;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Data
public class UnzipArtifact implements IComponentDeployer {

    private File outputFile;
    private File zipFile;

    @Override
    public DeploymentResult deploy() {
        if (!outputFile.exists()) outputFile.mkdirs();
        byte[] buffer = new byte[1024];
        try {
            @Cleanup
            FileInputStream fis = new FileInputStream(zipFile);
            @Cleanup
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFile + File.separator + fileName);
                new File(newFile.getParent()).mkdirs();
                @Cleanup
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            return DeploymentResult.OK;
        } catch (IOException e) {
            return DeploymentResult.FAILED;
        }
    }

    @Override
    public DeploymentResult undeploy() {
        return DeploymentResult.OK;
    }

    @Override
    public DeploymentResult stop() {
        return DeploymentResult.OK;
    }

    @Override
    public DeploymentResult start() {
        return DeploymentResult.OK;
    }

    @Override
    public void init(IDeploymentContext depCtx) {
        outputFile = new File(depCtx.getDeploymentURL().getFile());
        zipFile = depCtx.getArtifacts().get(depCtx.getMainArtifact());
    }
}
