package org.scm4j.commons.progress;

public interface IProgress extends AutoCloseable {
	IProgress createNestedProgress(String name);

	void reportStatus(String status);

	void error(String message);
	
	void startTrace(String message);
	
	void endTrace(String message);
	
	void trace(String message);
}
