package org.scm4j.deployer.installers;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

@Accessors(chain = true)
public class Exec implements IComponentDeployer {

	private String workingDirectory;

	@Setter
	private String executable;
	@Setter
	private String executablePrefix;
	@Getter
	private String[] args;
	private Event event = Event.ON_DEPLOY;
	@Setter
	private boolean ignoreExitValue;
	private int needRebootExitValue;

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

	public Exec setWorkingDirectory(String workingDirectory) {
		this.workingDirectory = workingDirectory;
		return this;
	}

	@SneakyThrows
	public static int exec(List<String> command, File directory) {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(directory);
		if (!directory.exists())
			directory.mkdirs();
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

	private DeploymentResult executeCommand(String executable, String[] args) {
		List<String> command = new ArrayList<>();
		command.add("cmd");
		command.add("/c");
		command.add(executable);
		int defaultRestartExitValue = 77;
		if (needRebootExitValue == 0)
			needRebootExitValue = defaultRestartExitValue;
		if (args != null) {
			for (String arg : args) {
				arg = StringUtils.replace(arg, "$deploymentPath", deploymentPath);
				arg = StringUtils.replace(arg, "$restartexitcode", String.valueOf(needRebootExitValue));
				arg = StringUtils.replace(arg, "\\", "/");
				command.add(arg);
			}
		}

		String workingDirectoryName = workingDirectory != null ? workingDirectory : deploymentPath;

		int exitValue = exec(command, new File(workingDirectoryName));

		if (exitValue == needRebootExitValue)
			return DeploymentResult.NEED_REBOOT;
		if (!ignoreExitValue && exitValue != 0)
			return DeploymentResult.FAILED;
		return DeploymentResult.OK;
	}

	public Exec setNeedRebootExitValue(int needRebootExitValue) {
		if (needRebootExitValue < 0 || needRebootExitValue == 0 || needRebootExitValue == 1)
			throw new IllegalArgumentException("Need reboot exit value can't be 0, 1 and below zero");
		this.needRebootExitValue = needRebootExitValue;
		return this;
	}

	private enum Event {ON_DEPLOY, ON_UNDEPLOY, ON_START, ON_STOP}

	@Override
	public DeploymentResult deploy() {
		if (event != Event.ON_DEPLOY || executable == null && defaultDeployExecutable == null)
			return DeploymentResult.OK;
		return executeCommand(executable == null ? defaultDeployExecutable.getPath() : executable, args);
	}

	@Override
	public DeploymentResult undeploy() {
		if (event != Event.ON_UNDEPLOY || executablePrefix == null)
			return DeploymentResult.OK;
		try {
			executable = findLatestUnins(deploymentPath, executablePrefix);
		} catch (Exception e) {
			return DeploymentResult.FAILED;
		}
		return executeCommand(executable, args);
	}

	private String findLatestUnins(String deploymentPath, String executablePrefix) throws IOException {
		try (Stream<Path> filePathStream = Files.walk(Paths.get(deploymentPath))) {
			return filePathStream.map(Path::getFileName)
					.map(Path::toString)
					.filter(p -> p.startsWith(executablePrefix))
					.filter(p -> p.endsWith(".exe"))
					.min(Comparator.reverseOrder())
					.orElseThrow(() -> new RuntimeException("Can't find executable file to undeploy product"));
		}
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
