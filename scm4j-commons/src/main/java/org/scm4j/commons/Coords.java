package org.scm4j.commons;

public interface Coords {

	String getGroupId();

	String getArtifactId();

	String getExtension();

	String getClassifier();

	String toStringNoComment();

	String toString(String versionStr);

	String getComment();

	Version getVersion();

	String getName();
}
