package org.scm4j.deployer.installers;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Data
public class Executor implements IComponentDeployer {

	private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-tmp-executor");
	private File outputDir;
	private File product;
	private Map<String, Object> params;

	private ProcessBuilder createCmd() {
		ProcessBuilder builder = new ProcessBuilder();
		Set<Map.Entry<String, Object>> set = params.entrySet();
		builder.directory(TMP_DIR);
		List<String> cmds = new ArrayList<>();
		cmds.add("cmd");
		cmds.add("/c");
		cmds.add(product.getName());
		set.forEach(entry -> {
			if(entry.getValue()!= null)
				cmds.add(entry.toString());
			else
				cmds.add(StringUtils.substringBefore(entry.toString(),"="));
		});
		builder.command(cmds);
		return builder;
	}

	@Override
	public void deploy() {
		Utils.unzip(TMP_DIR, product);
		try {
			Process p = createCmd().start();
			StreamGobbler streamGobbler = new StreamGobbler(p.getInputStream(), System.out::println);
			Executors.newSingleThreadExecutor().submit(streamGobbler);
			int exitCode = p.waitFor();
			if(exitCode != 0)
				throw new RuntimeException("Can't install " + product.getName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void undeploy() {

	}

	@Override
	public void init(IDeploymentContext depCtx) {
		outputDir = new File(depCtx.getDeploymentURL().getFile());
		params = depCtx.getParams().get(this.getClass());
		product = depCtx.getArtifacts().get(depCtx.getMainArtifact());
	}

	private static class StreamGobbler implements Runnable {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines()
					.forEach(consumer);
		}
	}
}
