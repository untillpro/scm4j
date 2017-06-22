package org.scm4j.commons.progress;

public interface IProgress extends AutoCloseable {
	IProgress createNestedProgress(String name);

	void reportStatus(String status);

	void trace(String message);

	void error(String message);
}
