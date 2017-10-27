package org.scm4j.commons;

public interface ICoords {

	String getGroupId();

	String getArtifactId();

	String getExtension();

	String getClassifier();

	String toStringNoComment();

	String toString(String versionStr);

	String getComment();

	Version getVersion();
}
