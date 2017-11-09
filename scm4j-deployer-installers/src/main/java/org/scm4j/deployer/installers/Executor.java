package org.scm4j.deployer.installers;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.Command;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
public class Executor implements IComponentDeployer {

    private String mainArtifact;
    private File outputDir;
    private File product;
    private Map<String, Object> params;

    private ProcessBuilder createCmd(Command command) {
        String deploymentPath = "$deploymentPath";
        String uninstallerName = "unins000.exe";
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
                cmds.add(uninstallerName);
                break;
            default:
                throw new IllegalArgumentException();
        }
        Arrays.stream(params.get(command.name().toLowerCase()).toString().split("\\s"))
                .filter(str -> !str.equals(""))
                .map(str -> {
                    if (str.contains(deploymentPath)) {
                        str = StringUtils.replace(str, deploymentPath, outputDir.toString());
                        return StringUtils.replace(str, "\\", "/");
                    }
                    return str;
                })
                .forEach(cmds::add);
        builder.command(cmds);
        return builder;
    }

    @Override
    @SneakyThrows
    public int deploy() {
        Process p = createCmd(Command.DEPLOY).start();
        return p.waitFor();
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
        this.params = params;
        outputDir = new File(depCtx.getDeploymentURL().getFile());
        product = depCtx.getArtifacts().get(depCtx.getMainArtifact());
        mainArtifact = depCtx.getMainArtifact();
    }

    @Override
    public String toString() {
        return "org.scm4j.deployer.installers.Executor{" +
                "product=" + product.getName() +
                '}';
    }
}
