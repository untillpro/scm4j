package org.scm4j.deployer.installers;

import org.apache.commons.lang3.StringUtils;

public final class Common {

	public static String normalize(String path) {
		return StringUtils.replace(path, "\\", "/");
	}
}
