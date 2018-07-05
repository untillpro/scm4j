package org.scm4j.deployer.installers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.scm4j.deployer.api.DeploymentResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class UntillExec extends Exec {

	private static final String DEFAULT_STATUS_FILE_NAME = "C:/ProgramData/unTill/installer/ssf.txt";
	private static final String STATUS_FILE_PARAM = "/statusFile=";

	private String statusFileFolderName;

	public Exec setStatusFileFolderName(String statusFileFolderName) {
		this.statusFileFolderName = statusFileFolderName;
		return this;
	}

	@Override
	public DeploymentResult deploy() {
		List<String> args = new ArrayList<>(Arrays.asList(super.getArgs()));
		if (statusFileFolderName == null)
			statusFileFolderName = DEFAULT_STATUS_FILE_NAME;
		File statusFile = new File(statusFileFolderName);
		try {
			if (!statusFile.exists()) {
				statusFile.getParentFile().mkdirs();
				statusFile.createNewFile();
			}
			args.add(STATUS_FILE_PARAM + '\"' + statusFileFolderName + '\"');
			super.setArgs(args.toArray(new String[0]));
			DeploymentResult res = super.deploy();
			if (res == DeploymentResult.FAILED) {
				List<String> status = FileUtils.readLines(statusFile, "UTF-8");
				if (status.contains("status=restarting"))
					return DeploymentResult.REBOOT_CONTINUE;
				else
					return DeploymentResult.FAILED;
			}
			return res;
		} catch (Exception e) {
			log.warn(e.toString());
			return DeploymentResult.FAILED;
		} finally {
			FileUtils.deleteQuietly(statusFile);
		}
	}

	@Override
	public DeploymentResult undeploy() {
		super.undeploy();
		return DeploymentResult.OK;
	}
}
