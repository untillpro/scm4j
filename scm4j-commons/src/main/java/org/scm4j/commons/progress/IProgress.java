package org.scm4j.commons.progress;

public interface IProgress extends AutoCloseable {
	IProgress createNestedProgress(String name);

	void reportStatus(String status);

	void error(String message);
	
	IProgress startTrace(String startMessage, String endMessage);
}
