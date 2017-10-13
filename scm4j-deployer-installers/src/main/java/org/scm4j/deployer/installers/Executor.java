package org.scm4j.deployer.installers;

import lombok.Data;
import lombok.SneakyThrows;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;
import org.scm4j.deployer.installers.exception.EInstallationException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Data
public class Executor implements IComponentDeployer {

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
    private static final String UNINSTALLER_NAME = "unins000.exe";
    private String mainArtifact;
    private File outputDir;
    private File product;
    private Map<String, Object> params;

    private ProcessBuilder createCmd(Command command) {
        ProcessBuilder builder = new ProcessBuilder();
        List<String> cmds = new ArrayList<>();
        cmds.add("cmd");
        cmds.add("/c");
        switch (command) {
            case DEPLOY:
            case UPGRADE:
                builder.directory(TMP_DIR);
                cmds.add(product.getName());
                break;
            case UNDEPLOY:
                cmds.add(UNINSTALLER_NAME);
                break;
        }
        cmds.add(params.get(command.name().toLowerCase()).toString());
        builder.command(cmds);
        return builder;
    }

    @Override
    @SneakyThrows
    public void deploy() {
        Utils.unzip(TMP_DIR, product);
        Process p = createCmd(Command.DEPLOY).start();
        StreamGobbler streamGobbler = new StreamGobbler(p.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        int exitCode = p.waitFor();
        if (exitCode != 0)
            throw new EInstallationException("Can't install " + product.getName());
    }

    @Override
    public void undeploy() {

    }

    @SneakyThrows
    private static boolean isProcessRunning(String mainArtifact) {
        Process p = Runtime.getRuntime().exec("tasklist");
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(mainArtifact)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void init(IDeploymentContext depCtx) {
        outputDir = new File(depCtx.getDeploymentURL().getFile());
        params = depCtx.getParams().get(this.getClass());
        product = depCtx.getArtifacts().get(depCtx.getMainArtifact());
        mainArtifact = depCtx.getMainArtifact();
    }

    @Override
    public String toString() {
        return "Executor{" +
                "product=" + product.getName() +
                '}';
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }
}
