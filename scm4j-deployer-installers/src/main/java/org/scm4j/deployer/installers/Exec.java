package org.scm4j.deployer.installers;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;

@Accessors(chain = true)
@Slf4j
public class Exec implements IComponentDeployer {

	@Setter
	private String workingDirectory;
	@Setter
	private String executable;
	@Setter
	private String executablePrefix;
	@Setter
	private Map<String, String> env;
	@Getter
	private String[] args;
	@Setter
	private boolean ignoreExitValue;
	private int needRebootExitValue;
	private Event event = Event.ON_DEPLOY;

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

	@Getter
	private String mainArtifact;
	private String deploymentPath;
	private File defaultDeployExecutable;

	@SneakyThrows
	public static Process exec(List<String> command, Map<String, String> env, File directory) {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(directory);
		if (env != null && !env.isEmpty())
			builder.environment().putAll(env);
		if (!directory.exists())
			directory.mkdirs();
		Process p = builder.start();
		realInheritIO(p.getInputStream(), System.out);
		realInheritIO(p.getErrorStream(), System.err);
		return p;
	}

	private static void realInheritIO(final InputStream src, final PrintStream dest) {
		new Thread(() -> {
			try (Scanner sc = new Scanner(src)) {
				while (sc.hasNextLine()) {
					String line = sc.nextLine();
					dest.println(line);
					log.info(line);
				}
			}
		}).start();
	}

	@SneakyThrows
	private DeploymentResult executeCommand(String executable, String[] args, Map<String, String> env) {
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

		Process p = exec(command, env, new File(workingDirectoryName));
		int exitValue = p.waitFor();

		log.info("Exit value is " + exitValue);

		if (exitValue == needRebootExitValue)
			return DeploymentResult.NEED_REBOOT;
		if (!ignoreExitValue && exitValue != 0) {
			DeploymentResult dr = DeploymentResult.FAILED;
			dr.setErrorMsg(errorStreamToString(p));
			return DeploymentResult.FAILED;
		}
		return DeploymentResult.OK;
	}

	@SneakyThrows
	private String errorStreamToString(Process p) {
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
		return sb.toString();
	}

	public Exec setArgs(String... args) {
		this.args = args;
		return this;
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
		return executeCommand(executable == null ? defaultDeployExecutable.getPath() : executable, args, env);
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
		return executeCommand(executable, args, env);
	}

	private String findLatestUnins(String deploymentPath, String executablePrefix) throws IOException {
		try (Stream<Path> filePathStream = Files.walk(Paths.get(deploymentPath))) {
			return filePathStream.map(Path::getFileName)
					.map(Path::toString)
					.filter(p -> p.startsWith(executablePrefix))
					.filter(p -> p.endsWith(".exe"))
					.max(Comparator.naturalOrder())
					.orElseThrow(() -> new RuntimeException("Can't find executable file to undeploy product"));
		}
	}

	@Override
	public DeploymentResult stop() {
		if (event != Event.ON_STOP || executable == null)
			return DeploymentResult.OK;
		return executeCommand(executable, args, env);
	}

	@Override
	public DeploymentResult start() {
		if (event != Event.ON_START || executable == null)
			return DeploymentResult.OK;
		return executeCommand(executable, args, env);
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
