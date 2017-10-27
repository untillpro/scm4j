package org.scm4j.commons.coords;

import org.scm4j.commons.Version;

public interface ICoords {

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
