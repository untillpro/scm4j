package org.scm4j.commons;

import org.apache.commons.lang3.StringUtils;

public class Coords {

	private final String artifactId;
	private final String commentStr;
	private final String extension;
	private final String groupId;
	private final String classifier;
	private final Version version;
	private final String verPrefix;
	private final String verSuffix;
	private final String coordsStringNoComment;

	public String getComment() {
		return commentStr;
	}

	public String ltrim(String s) {
		int i = 0;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return s.substring(i);
	}

	public String rtrim(String s) {
		int i = s.length() - 1;
		while (i > 0 && Character.isWhitespace(s.charAt(i))) {
			i--;
		}
		return s.substring(0, i + 1);
	}

	public Coords(String coordsString) {
		String str = coordsString;
		// Comment
		{
			Integer pos = coordsString.indexOf("#");

			if (pos > 0) {
				// add spaces between valuable chars and # to comment
				commentStr = StringUtils.difference(str.substring(0, pos).trim(), ltrim(str.substring(0, pos)))
						+ str.substring(pos);
				str = rtrim(str.substring(0, pos));
				this.coordsStringNoComment = str.trim();
			} else {
				commentStr = "";
				this.coordsStringNoComment = coordsString;
			}
		}

		// Extension
		{
			Integer pos = coordsString.indexOf("@");
			if (pos > 0) {
				extension = str.substring(pos).trim();
				str = str.substring(0, pos);
			} else {
				extension = "";
			}
		}

		String[] strs = str.split(":", -1);
		if (strs.length < 2) {
			throw new IllegalArgumentException("wrong mdep coord: " + coordsString);
		}

		groupId = strs[0];
		artifactId = strs[1];

		verPrefix = strs.length > 2 ? ":" : "";

		verSuffix = strs.length > 3 ? ":" : "";

		classifier = strs.length > 3 ? strs[3].trim() : "";

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
		Coords other = (Coords) obj;
		if (coordsStringNoComment == null) {
			if (other.coordsStringNoComment != null)
				return false;
		} else if (!coordsStringNoComment.equals(other.coordsStringNoComment))
			return false;
		return true;
	}

	public Version getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return toString(version.toString());
	}

	public String toString(String versionStr) {
		String str = verPrefix.isEmpty() && !versionStr.isEmpty() ? ":" : verPrefix;
		str += versionStr + verSuffix + classifier + extension;
		return getName() + str + commentStr;
	}

	public String getName() {
		return groupId + ":" + artifactId;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getExtension() {
		return extension;
	}

	public String getClassifier() {
		return classifier;
	}
}
