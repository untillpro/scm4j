package org.scm4j.deployer.installers;

import lombok.extern.slf4j.Slf4j;
import mslinks.ShellLink;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.scm4j.deployer.api.DeploymentResult.FAILED;
import static org.scm4j.deployer.api.DeploymentResult.OK;

@Slf4j
public class Shortcut implements IComponentDeployer {

	private String shortcutName;
	private String image;
	private String pathToExistingFile;
	private Optional<String> args;
	private Optional<String> deploymentPath;

	public Shortcut setShortcutName(String shortcutName) {
		this.shortcutName = shortcutName;
		return this;
	}

	public Shortcut setImage(String image) {
		this.image = image;
		return this;
	}

	public Shortcut setPathToExistingFile(String pathToExistingFile) {
		this.pathToExistingFile = pathToExistingFile;
		return this;
	}

	public Shortcut setArgs(String args) {
		this.args = Optional.of(args);
		return this;
	}

	public Shortcut setDeploymentPath(String deploymentPath) {
		this.deploymentPath = Optional.of(deploymentPath);
		return this;
	}

	@Override
	public DeploymentResult deploy() {
		ShellLink sl = ShellLink.createLink(pathToExistingFile)
				.setCMDArgs(args.orElse(""));
		if (image != null)
			sl.setIconLocation(image);
		File dest = new File(deploymentPath.orElse(System.getProperty("user.home") + "/Desktop"));
		if (!dest.exists())
			dest.mkdirs();
		try {
			String destFile = dest.getPath().replace('\\', '/') + '/' + shortcutName + ".lnk";
			sl.saveTo(destFile);
		} catch (IOException e) {
			log.warn(e.getMessage());
			return FAILED;
		}
		return OK;
	}

	@Override
	public DeploymentResult undeploy() {
		File dest = new File(deploymentPath.orElse(System.getProperty("user.home") + "/Desktop"));
		File shortcutFile = new File(dest, shortcutName + ".lnk");
		FileUtils.deleteQuietly(shortcutFile);
		if (shortcutFile.exists())
			return FAILED;
		else
			return OK;
	}

	@Override
	public DeploymentResult stop() {
		return OK;
	}

	@Override
	public DeploymentResult start() {
		return OK;
	}

	@Override
	public void init(IDeploymentContext depCtx) {
		shortcutName = StringUtils.substringBeforeLast(depCtx.getMainArtifact(), "-");
		args = Optional.empty();
		deploymentPath = Optional.empty();
	}
}
