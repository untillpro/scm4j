package org.scm4j.commons;

import org.apache.commons.lang3.StringUtils;

public class Version {

	public static final String SNAPSHOT = "-SNAPSHOT";

	private final String minor;
	private final String prefix;
	private final String snapshot;
	private final String patch;
	private final String verStr;
	private final boolean isEmpty;
	private final boolean isSemantic;

	public Version(String verStr) {
		verStr = verStr.trim();
		this.verStr = verStr;
		if (verStr.isEmpty()) {
			snapshot = "";
			prefix = "";
			minor = "";
			patch = "";
			isEmpty = true;
			isSemantic = false;
		} else {
			isEmpty = false;
			if (verStr.contains(SNAPSHOT)) {
				snapshot = SNAPSHOT;
				verStr = verStr.replace(SNAPSHOT, "");
			} else {
				snapshot = "";
			}
			if (verStr.lastIndexOf(".") > 0) {
				patch = verStr.substring(verStr.lastIndexOf(".") + 1, verStr.length());
				verStr = verStr.substring(0, verStr.lastIndexOf("."));
				if (verStr.lastIndexOf(".") > 0) {
					minor = verStr.substring(verStr.lastIndexOf(".") + 1, verStr.length());
				} else {
					minor = verStr;
				}
				prefix = verStr.substring(0, verStr.lastIndexOf(".") + 1);
			} else {
				prefix = "";
				minor = verStr;
				patch = "";
			}
			isSemantic = StringUtils.isNumeric(minor);
		}
	}

	public String getPatch() {
		return patch;
	}

	public String getMinor() {
		return minor;
	}

	public String getSnapshot() {
		return snapshot;
	}

	@Override
	public String toString() {
		if (!isSemantic) {
			return verStr;
		}
		return prefix + minor + (patch.isEmpty() ? "" : "." + patch) + snapshot;
	}
	
	public Version toPreviousPatch() {
		if (patch.isEmpty()) {
			return new Version(prefix + minor + ".0" + snapshot);
		}
		if (!StringUtils.isNumeric(patch)) {
			return this;
		}
		int patchInt = Integer.parseInt(patch) - 1;
		return new Version(prefix + minor + "." + Integer.toString(patchInt) + snapshot);
	}

	public Version toNextPatch() {
		if (patch.isEmpty()) {
			return new Version(prefix + minor + ".1" + snapshot);
		}
		if (!StringUtils.isNumeric(patch)) {
			return this;
		}
		int patchInt = Integer.parseInt(patch) + 1;
		return new Version(prefix + minor + "." + Integer.toString(patchInt) + snapshot);
	}

	public Version toPreviousMinor() {
		checkSemantic();
		return new Version(prefix + Integer.toString(Integer.parseInt(minor) - 1) + "." + patch + snapshot);
	}

	public Version toNextMinor() {
		checkSemantic();
		return new Version(prefix + Integer.toString(Integer.parseInt(minor) + 1) + "." + patch + snapshot);
	}

	private void checkSemantic() {
		if (!isSemantic) {
			throw new IllegalStateException("wrong version" + verStr);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Version other = (Version) obj;
		if (verStr == null) {
			if (other.verStr != null)
				return false;
		} else if (!verStr.equals(other.verStr))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((verStr == null) ? 0 : verStr.hashCode());
		return result;
	}

	public Boolean isEmpty() {
		return isEmpty;
	}
	
	@Deprecated
	public boolean isExact() {
		return isLocked();
	}
	
	public boolean isLocked() {
		return isSemantic && !isSnapshot();
	}

	public boolean isSnapshot() {
		return !snapshot.isEmpty();
	}
	
	public String toSnapshotString() {
		return prefix + minor + (patch.isEmpty() ? "" : "." + patch) + SNAPSHOT;
	}

	public Version toSnapshot() {
		return new Version(toSnapshotString());
	}
	
	public Version toRelease() {
		if (!isSemantic) {
			return this;
		}
		return new Version(prefix + minor + (patch.isEmpty() ? "" : "." + patch));
	}
	
	public String toReleaseString() {
		return toRelease().toString();
	}
	
	public Boolean isGreaterThan(Version other) {
		if (other.isEmpty()) {
			return !isEmpty();
		}
		if (!isSemantic || !StringUtils.isNumeric(getMinor())) {
			return false;
		}
		if (!other.isSemantic()) {
			return true;
		}
		
		int minor = Integer.parseInt(getMinor());
		int otherMinor = Integer.parseInt(other.getMinor());
		if (minor > otherMinor) {
			return true;
		}
		if (minor < otherMinor) {
			return false;
		}

		if (!StringUtils.isNumeric(getPatch()) || !StringUtils.isNumeric(other.getPatch())) {
			return false;
		}

		int patch = Integer.parseInt(getPatch());
		int otherPatch = Integer.parseInt(other.getPatch());

		return patch > otherPatch;

	}

	public boolean isSemantic() {
		return isSemantic;
	}

	public String getReleaseNoPatchString() {
		return prefix + minor;
	}

	public Version setMinor(String minor) {
		if (!isSemantic) {
			throw new IllegalStateException("can not set minor for non-semantic version");
		}
		return new Version(prefix + minor + (patch.isEmpty() ? "" : "." + patch) + snapshot);
		
	}

	public Version setPatch(String patch) {
		if (!isSemantic) {
			throw new IllegalStateException("can not set patch for non-semantic version");
		}
		return new Version(prefix + minor + (patch.isEmpty() ? "" : "." + patch) + snapshot);
	}

	public Version toReleaseZeroPatch() {
		return toRelease().setPatch("0");
	}

	public Version toReleaseNoPatch() {
		return new Version(getReleaseNoPatchString());
	}
}