package org.scm4j.deployer.installers;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;
import org.scm4j.deployer.installers.exception.EParamsNotFoundException;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Accessors(chain = true)
public class ProcrunDeployer implements IComponentDeployer {

	@Setter
	private String serviceName;
	@Setter
	private String jvm;
	@Setter
	private String jvmOptions;
	@Setter
	private String classpath;
	@Setter
	private String startClass;
	@Setter
	private String startMethod;
	@Setter
	private String startParams;
	@Setter
	private String stopClass;
	@Setter
	private String stopMethod;
	@Setter
	private String stopParams;

	@Setter
	private String logPath;
	@Setter
	private String logPrefix;
	@Setter
	private String logLevel;

	private String deploymentPath;
	private File mainArtifact;

	private Path getServiceExePath() {
		return Paths.get(deploymentPath, serviceName + ".exe");
	}

	private Path getMonitorExePath() {
		return Paths.get(deploymentPath, serviceName + "w.exe");
	}

	@Override
	public void init(IDeploymentContext depCtx) {
		if (serviceName == null)
			throw new EParamsNotFoundException("Can't find serviceName for this operation");
		deploymentPath = depCtx.getDeploymentPath();
		mainArtifact = depCtx.getArtifacts().get(depCtx.getMainArtifact());
	}

	@Override
	@SneakyThrows
	public DeploymentResult deploy() {
		// unzip and rename
		try (FileSystem fileSystem = FileSystems.newFileSystem(mainArtifact.toPath(), null)) {
			Path prunsrv = fileSystem.getPath("prunsrv.exe");
			Path prunmgr = fileSystem.getPath("prunmgr.exe");
			Files.copy(prunsrv, getServiceExePath(), StandardCopyOption.REPLACE_EXISTING);
			Files.copy(prunmgr, getMonitorExePath(), StandardCopyOption.REPLACE_EXISTING);
		}

		// install
		List<String> cmd = new ArrayList<>();
		cmd.addAll(Arrays.asList(getServiceExePath().toString(), "install",
				"--StartMode", "jvm",
				"--StopMode", "jvm",
				"--Startup", "auto"));
		if (jvm != null) cmd.addAll(Arrays.asList("--Jvm", jvm));
		if (jvmOptions != null) cmd.addAll(Arrays.asList("--JvmOptions", jvmOptions));
		if (classpath != null) cmd.addAll(Arrays.asList("--Classpath", classpath));
		if (startClass != null) cmd.addAll(Arrays.asList("--StartClass", startClass));
		if (startMethod != null) cmd.addAll(Arrays.asList("--StartMethod", startMethod));
		if (startParams != null) cmd.addAll(Arrays.asList("--StartParams", startParams));
		if (stopClass != null) cmd.addAll(Arrays.asList("--StopClass", stopClass));
		if (stopMethod != null) cmd.addAll(Arrays.asList("--StopMethod", stopMethod));
		if (stopParams != null) cmd.addAll(Arrays.asList("--StopParams", stopParams));
		if (logPath != null) cmd.addAll(Arrays.asList("--LogPath", logPath));
		if (logPrefix != null) cmd.addAll(Arrays.asList("--LogPrefix", logPrefix));
		if (logLevel != null) cmd.addAll(Arrays.asList("--LogLevel", logLevel));
		execute(cmd);
		return DeploymentResult.OK; // TODO 8 = DeploymentResult.NEED_REBOOT
	}

	@Override
	@SneakyThrows
	public DeploymentResult undeploy() {
		execute(Arrays.asList(getServiceExePath().toString(), "delete"));
		// TODO delete
		Files.delete(getServiceExePath());
		Files.delete(getMonitorExePath());
		return null;
	}

	@Override
	public DeploymentResult start() {
		execute(Arrays.asList(getServiceExePath().toString(), "start"));
		return DeploymentResult.OK;
	}

	@Override
	public DeploymentResult stop() {
		execute(Arrays.asList(getServiceExePath().toString(), "stop"));
		return DeploymentResult.OK;
	}

	@SneakyThrows
	private void execute(List<String> command) {
		int res = Exec.exec(command, null, new File(deploymentPath)).waitFor();
		if (res != 0)
			throw new Exception(String.format("%s: %d", command.toString(), res));
	}

}
