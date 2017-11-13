package org.scm4j.deployer.installers;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
public class Executor implements IComponentDeployer {

    private String mainArtifact;
    private File outputDir;
    private File product;
    private Map<String, Object> params;
    private Function<String, ProcessBuilder> cmdToProcessBuilder = str -> {
        String deploymentPath = "$deploymentPath";
        ProcessBuilder builder = new ProcessBuilder();
        List<String> cmds = new ArrayList<>();
        cmds.add("cmd");
        cmds.add("/c");
        builder.directory(product.getParentFile());
        cmds.add(product.getName());
        Arrays.stream(params.get(str.toLowerCase()).toString().split("\\s(?=/)"))
                .filter(param -> !param.equals(""))
                .map(param -> StringUtils.replace(param, deploymentPath, outputDir.toString()))
                .map(param -> StringUtils.replace(param, "\\", "/"))
                .forEach(cmds::add);
        builder.command(cmds);
        return builder;
    };

    @SneakyThrows
    private DeploymentResult executeCommand(String param) {
        Process p = cmdToProcessBuilder.apply(param).start();
        int code = p.waitFor();
        return code == 0 ? DeploymentResult.OK : code == 1 || code == 777 ? DeploymentResult.NEED_REBOOT : DeploymentResult.FAILED;
    }

    @Override
    @SneakyThrows
    public DeploymentResult deploy() {
        return executeCommand("deploy");
    }

    @Override
    @SneakyThrows
    public DeploymentResult undeploy() {
        product = (File) params.get("uninstaller");
        return executeCommand("undeploy");
    }

    @Override
    @SneakyThrows
    public DeploymentResult stop() {
        return DeploymentResult.OK;
    }

    @Override
    @SneakyThrows
    public DeploymentResult start() {
        return DeploymentResult.OK;
    }

    @Override
    public void init(IDeploymentContext depCtx, Map<String, Object> params) {
        this.params = params;
        outputDir = new File(depCtx.getDeploymentURL().getPath());
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
