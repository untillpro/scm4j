package org.scm4j.deployer.installers;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
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

    @Override
    @SneakyThrows
    public int deploy() {
        Process p = cmdToProcessBuilder.apply("deploy").start();
        return p.waitFor();
    }

    @Override
    @SneakyThrows
    public int undeploy() {
        product = (File) params.get("uninstaller");
        Process p = cmdToProcessBuilder.apply("undeploy").start();
        return p.waitFor();
    }

    @Override
    @SneakyThrows
    public int stop() {
        return 0;
    }

    @Override
    @SneakyThrows
    public int start() {
        return 0;
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
