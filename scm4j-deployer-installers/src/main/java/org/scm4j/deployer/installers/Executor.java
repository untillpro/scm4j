package org.scm4j.deployer.installers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import lombok.Data;
import lombok.SneakyThrows;

@Data
public class Executor implements IComponentDeployer {

    private String mainArtifact;
    private File outputDir;
    private File executable;
    private Map<String, Object> params;
    
    
    protected ProcessBuilder getBuilder(String str, File executableFile) {
    	if(null == executableFile) {
    		executableFile = executable;
    	}
        String deploymentPath = "$deploymentPath";
        ProcessBuilder builder = new ProcessBuilder();
        List<String> cmds = new ArrayList<>();
        cmds.add("cmd");
        cmds.add("/c");
        builder.directory(executable.getParentFile());
        cmds.add(executable.getName());
        Arrays.stream(params.get(str.toLowerCase()).toString().split("\\s(?=/)"))
                .filter(param -> !param.equals(""))
                .map(param -> StringUtils.replace(param, deploymentPath, outputDir.toString()))
                .map(param -> StringUtils.replace(param, "\\", "/"))
                .forEach(cmds::add);
        builder.command(cmds);
        return builder;    	
    }
    
    protected ProcessBuilder getBuilder(String str) {
    	return getBuilder(str, null);
    };

    @SneakyThrows
    private DeploymentResult executeCommand(String param, File executableFile) {
    	Process p = getBuilder(param, executableFile).start();
        int code = p.waitFor();
        return code == 0 ? DeploymentResult.OK : code == 1 || code == 777 ? DeploymentResult.NEED_REBOOT : DeploymentResult.FAILED;
    }
    
    @SneakyThrows
    private DeploymentResult executeCommand(String param) {
    	return executeCommand(param, null);        
    }

    @Override
    @SneakyThrows
    public DeploymentResult deploy() {
        return executeCommand("deploy");
    }

    @Override
    @SneakyThrows
    public DeploymentResult undeploy() {
        File executableFile = (File) params.get("undeployer");
        return executeCommand("undeploy", executableFile);
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
        executable = depCtx.getArtifacts().get(depCtx.getMainArtifact());
        mainArtifact = depCtx.getMainArtifact();
    }

    @Override
    public String toString() {
        return "org.scm4j.deployer.installers.Executor{" +
                "product=" + executable.getName() +
                '}';
    }
}
