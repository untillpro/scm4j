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

	private enum Event {ON_DEPLOY, ON_UNDEPLOY, ON_START, ON_STOP};
	
	@Setter
	private String executable;
	private String[] args;
	private Event event = Event.ON_DEPLOY;
	@Setter
	private boolean ignoreExitValue;
	private int[] needRebootExitValues;

	public Exec onDeploy() {
		event = Event.ON_DEPLOY;
		return this;
	}

	public Exec onUndeploy() {
		event = Event.ON_UNDEPLOY;
		return this;
	}

	public Exec onStart() {
		event = Event.ON_START;
		return this;
	}

	public Exec onStop() {
		event = Event.ON_STOP;
		return this;
	}

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

	public Exec setArgs(String... args) {
		this.args = args;
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
		if (event != Event.ON_DEPLOY || executable == null && defaultDeployExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(executable == null ? defaultDeployExecutable.getPath() : executable, args);
	}

	@Override
	public DeploymentResult undeploy() {
		if (event != Event.ON_UNDEPLOY || executable == null)
			return DeploymentResult.OK;
		return executeCommand(executable, args);
	}

	@Override
	public DeploymentResult stop() {
		if (event != Event.ON_STOP || executable == null)
			return DeploymentResult.OK;
		return executeCommand(executable, args);
	}

	@Override
	public DeploymentResult start() {
		if (event != Event.ON_START || executable == null)
			return DeploymentResult.OK;
		return executeCommand(executable, args);
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
