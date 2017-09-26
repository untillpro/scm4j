package org.scm4j.deployer.installers;

import lombok.Cleanup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

    public static void unzip(File outputFile, File zipFile) {
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
}
