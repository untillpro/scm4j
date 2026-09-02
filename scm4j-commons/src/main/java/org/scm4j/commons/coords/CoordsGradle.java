package org.scm4j.commons.coords;

import org.apache.commons.lang3.StringUtils;
import org.scm4j.commons.CommentedString;
import org.scm4j.commons.Version;

public class CoordsGradle implements Coords {

	private final String sourceStr;
	private final String artifactId;
	private final String commentStr;
	private final String extension;
	private final String groupId;
	private final String classifier;
	private final Version version;
	private final String coordsStringNoComment;

	public CoordsGradle(String coordsString) {
		sourceStr = coordsString;
		CommentedString cs = new CommentedString(coordsString);
		commentStr = cs.getComment();
		coordsStringNoComment = cs.getStrNoComment();

		coordsString = coordsStringNoComment;

		// Extension
		{
			Integer pos = coordsString.indexOf("@");
			if (pos > 0) {
				extension = coordsString.substring(pos).trim();
				coordsString = coordsString.substring(0, pos);
			} else {
				extension = "";
			}
		}

		String[] strs = coordsString.split(":", -1);
		if (strs.length < 2) {
			throw new IllegalArgumentException("wrong mdep coord: " + coordsString);
		}

		groupId = strs[0];

		artifactId = ":" + strs[1];

		classifier = strs.length > 3 ? ":" + strs[3].trim() : "";

		version = new Version(strs.length > 2 ? strs[2] : "");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((coordsStringNoComment == null) ? 0 : coordsStringNoComment.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CoordsGradle other = (CoordsGradle) obj;
		if (coordsStringNoComment == null) {
			if (other.coordsStringNoComment != null)
				return false;
		} else if (!coordsStringNoComment.equals(other.coordsStringNoComment))
			return false;
		return true;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public String getName() {
		return groupId + artifactId;
	}

	@Override
	public String toString() {
		return sourceStr;
	}

	@Override
	public String toString(String versionStr) {
		String name = groupId + artifactId;
		if (!versionStr.isEmpty() || ((!name.isEmpty() && !(classifier + extension).isEmpty()))) {
			versionStr = ":" + versionStr;
		}
		return getName() + versionStr + classifier + extension + commentStr;
	}

	@Override
	public String getGroupId() {
		return groupId;
	}

	@Override
	public String getArtifactId() {
		return StringUtils.removeStart(artifactId, ":");
	}

	@Override
	public String getExtension() {
		return StringUtils.removeStart(extension, "@");
	}

	@Override
	public String getClassifier() {
		return StringUtils.removeStart(classifier, ":");
	}

	@Override
	public String getComment() {
		return commentStr;
	}

	@Override
	public String toStringNoComment() {
		return coordsStringNoComment;
	}
}
