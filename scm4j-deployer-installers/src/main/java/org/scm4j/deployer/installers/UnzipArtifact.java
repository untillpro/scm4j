package org.scm4j.deployer.installers;

import lombok.Cleanup;
import lombok.Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Data
public class UnzipArtifact implements IDeployer {

    private File outputFile;
    private File zipFile;

    public UnzipArtifact(File outputFile, File zipFile) {
        this.outputFile = outputFile;
        this.zipFile = zipFile;
    }

    public void unzip(File outputFile, File zipFile) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deploy() {
        unzip(outputFile,zipFile);
    }

    @Override
    public void unDeploy() {

    }

    @Override
    public boolean canDeploy() {
        return false;
    }

    @Override
    public boolean checkIntegrity() {
        return false;
    }
}
