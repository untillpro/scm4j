package org.scm4j.deployer.installers;

import lombok.Cleanup;
import lombok.Data;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Data
public class UnzipArtifact implements IComponentDeployer {

    private File outputFile;
    private File zipFile;

    @Override
    public int deploy() {
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
            return 0;
        } catch (IOException e) {
            return 1;
        }
    }

    @Override
    public int undeploy() {
        return 0;
    }

    @Override
    public int stop() {
        return 0;
    }

    @Override
    public int start() {
        return 0;
    }

    @Override
    public void init(IDeploymentContext depCtx, Map<String, Object> params) {
        outputFile = new File(depCtx.getDeploymentURL().getFile());
        zipFile = depCtx.getArtifacts().get(depCtx.getMainArtifact());
    }
}
