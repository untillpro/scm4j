package org.scm4j.releaser.conf;

import java.util.Objects;

public final class VCSRepositoryId {

	private final String url;
	private final String subfolder;

	public VCSRepositoryId(String url, String subfolder) {
		this.url = url;
		this.subfolder = normalizeSubfolder(subfolder);
	}

	public String getUrl() {
		return url;
	}

	public String getSubfolder() {
		return subfolder;
	}

	private static String normalizeSubfolder(String subfolder) {
		if (subfolder == null || subfolder.isEmpty()) {
			return "";
		}
		String normalized = subfolder.replace('\\', '/');
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		VCSRepositoryId other = (VCSRepositoryId) obj;
		return Objects.equals(url, other.url) && Objects.equals(subfolder, other.subfolder);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, subfolder);
	}

	@Override
	public String toString() {
		return subfolder.isEmpty() ? url : url + " [" + subfolder + "]";
	}
}
