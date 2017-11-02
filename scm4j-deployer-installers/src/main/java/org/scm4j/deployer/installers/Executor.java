package org.scm4j.deployer.installers;

import lombok.Data;
import lombok.SneakyThrows;
import org.scm4j.deployer.api.Command;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;
import org.scm4j.deployer.installers.exception.EInstallationException;

import java.io.File;
import java.util.*;

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
                builder.directory(product.getParentFile());
                cmds.add(product.getName());
                break;
            case UNDEPLOY:
                cmds.add(UNINSTALLER_NAME);
                break;
        }
        Arrays.stream(params.get(command.name().toLowerCase()).toString().split("\\s(?=/)"))
                .filter(str -> !str.equals(""))
                .forEach(cmds::add);
        builder.command(cmds);
        return builder;
    }

    @Override
    @SneakyThrows
    public void deploy() {
        Process p = createCmd(Command.DEPLOY).start();
        int exitCode = p.waitFor();
        if (exitCode != 0)
            throw new EInstallationException("Can't install " + product.getName());
    }

    @Override
    public void undeploy() {

    }

    @Override
    public void init(IDeploymentContext depCtx, Map<String,Object> params) {
        this.params = params;
        outputDir = new File(depCtx.getDeploymentURL().getFile());
        product = depCtx.getArtifacts().get(depCtx.getMainArtifact());
        mainArtifact = depCtx.getMainArtifact();
    }

    @Override
    public String toString() {
        return "Executor{" +
                "product=" + product.getName() +
                '}';
    }
}
