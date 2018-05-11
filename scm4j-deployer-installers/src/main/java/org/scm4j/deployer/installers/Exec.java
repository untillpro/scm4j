package org.scm4j.deployer.installers;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

@Accessors(chain = true)
public class Exec implements IComponentDeployer {

	@Setter
	private String deployExecutable;
	private String[] deployArgs;
	@Setter
	private String undeployExecutable;
	private String[] undeployArgs;
	@Setter
	private String startExecutable;
	private String[] startArgs;
	@Setter
	private String stopExecutable;
	private String[] stopArgs;

	@Setter
	private boolean ignoreExitValue;
	private int[] needRebootExitValues;

	private String mainArtifact;
	private String deploymentPath;
	private File defaultDeployExecutable;

	@SneakyThrows
	public static int exec(List<String> command, File directory) {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(directory);
		Process p = builder.start();
		realInheritIO(p.getInputStream(), System.out);
		realInheritIO(p.getErrorStream(), System.err);
		return p.waitFor();
	}

	private static void realInheritIO(final InputStream src, final PrintStream dest) {
		new Thread(() -> {
			try (Scanner sc = new Scanner(src)) {
				while (sc.hasNextLine())
					dest.println(sc.nextLine());
			}
		}).start();
	}

	public Exec setDeployArgs(String... deployArgs) {
		this.deployArgs = deployArgs;
		return this;
	}

	public Exec setUndeployArgs(String... undeployArgs) {
		this.undeployArgs = undeployArgs;
		return this;
	}

	public Exec setStartArgs(String... startArgs) {
		this.startArgs = startArgs;
		return this;
	}

	public Exec setStopArgs(String... stopArgs) {
		this.stopArgs = stopArgs;
		return this;
	}

	public Exec setNeedRebootExitValues(int... needRebootExitValues) {
		this.needRebootExitValues = needRebootExitValues;
		return this;
	}

	private DeploymentResult executeCommand(String executable, String[] args) {
		List<String> command = new ArrayList<>();
		command.add(executable);
		if (args != null) {
			for (String arg : args) {
				arg = StringUtils.replace(arg, "$deploymentPath", deploymentPath);
				arg = StringUtils.replace(arg, "\\", "/");
				command.add(arg);
			}
		}

		int exitValue = exec(command, new File(deploymentPath));

		if (needRebootExitValues != null && IntStream.of(needRebootExitValues).anyMatch(i -> exitValue == i))
			return DeploymentResult.NEED_REBOOT;
		if (!ignoreExitValue && exitValue != 0)
			return DeploymentResult.FAILED;
		return DeploymentResult.OK;
	}

	@Override
	public DeploymentResult deploy() {
		if (deployExecutable == null && defaultDeployExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(deployExecutable == null ? defaultDeployExecutable.getPath() : deployExecutable, deployArgs);
	}

	@Override
	public DeploymentResult undeploy() {
		if (undeployExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(undeployExecutable, undeployArgs);
	}

	@Override
	public DeploymentResult stop() {
		if (stopExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(stopExecutable, stopArgs);
	}

	@Override
	public DeploymentResult start() {
		if (startExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(startExecutable, startArgs);
	}

	@Override
	public void init(IDeploymentContext depCtx) {
		mainArtifact = depCtx.getMainArtifact();
		deploymentPath = depCtx.getDeploymentPath();
		if (depCtx.getArtifacts() != null)
			defaultDeployExecutable = depCtx.getArtifacts().get(mainArtifact);
	}

	@Override
	public String toString() {
		return "Exec{product=" + mainArtifact + '}';
	}

}
