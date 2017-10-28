package org.scm4j.commons.coords;

import org.apache.commons.lang3.StringUtils;
import org.scm4j.commons.CommentedString;
import org.scm4j.commons.Coords;
import org.scm4j.commons.Version;

public class CoordsMaven implements Coords {

	private final String sourceStr;
	private final String artifactId;
	private final String commentStr;
	private final String extension;
	private final String groupId;
	private final String classifier;
	private final Version version;
	private final String coordsStringNoComment;

	public CoordsMaven(String coordsString) {
		// groupId:artifactId[:extension[:classifier]]:version
		sourceStr = coordsString;

		CommentedString cs = new CommentedString(coordsString);
		commentStr = cs.getComment();
		coordsStringNoComment = cs.getStrNoComment();

		coordsString = coordsStringNoComment;
		String[] strs = coordsString.split(":", -1);
		if (strs.length < 2) {
			throw new IllegalArgumentException("wrong mdep coord: " + coordsString);
		}

		groupId = strs[0];

		artifactId = ":" + strs[1];

		extension = strs.length > 3 ? ":" + strs[2] : "";

		classifier = strs.length > 4 ? ":" + strs[3] : "";

		version = new Version(strs.length > 2 ? strs[strs.length - 1] : "");
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
		return StringUtils.removeStart(extension, ":");
	}

	@Override
	public String getClassifier() {
		return StringUtils.removeStart(classifier, ":");
	}

	@Override
	public String toStringNoComment() {
		return coordsStringNoComment;
	}

	@Override
	public String toString(String versionStr) {
		if (!versionStr.isEmpty()) {
			versionStr = ":" + versionStr;
		}
		return getName() + extension + classifier + versionStr +  commentStr;
	}

	@Override
	public String toString() {
		return sourceStr;
	}

	@Override
	public String getComment() {
		return commentStr;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public String getName() {
		return groupId + artifactId;
	}
}
