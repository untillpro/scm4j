package org.scm4j.commons.progress;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.io.PrintStream;

import com.google.common.base.Strings;

public class SingleLineProgressConsole implements IProgress {
	
	private final String endMessage;
	private final PrintStream out;
	
	

	public SingleLineProgressConsole(PrintStream out, String startMessage, String endMessage) {
		this(0, out, startMessage, endMessage);
	}
	
	public SingleLineProgressConsole(Integer level, PrintStream out, String startMessage, String endMessage) {
		this.endMessage = endMessage;
		this.out = out;
		out.print(Strings.repeat("\t", level));
		reportStatus(startMessage);
	}
	
	public SingleLineProgressConsole(String startMessage, String endMessage) {
		this(0, System.out, startMessage, endMessage);
	}

	@Override
	public void close() throws Exception {
		getOut().println(getEndMessage());
	}

	@Override
	public IProgress createNestedProgress(String name) {
		return new SingleLineProgressConsole(getOut(), name, getEndMessage()); 
	}

	@Override
	public void reportStatus(String status) {
		getOut().print(status);
		getOut().flush();
	}

	@Override
	public void error(String message) {
		getOut().print(ansi().fg(RED).a(message).reset().toString());
		getOut().flush();
	}

	@Override
	public IProgress startTrace(String startMessage, String endMessage) {
		return new SingleLineProgressConsole(getOut(), startMessage, endMessage); 
	}

	public String getEndMessage() {
		return endMessage;
	}

	public PrintStream getOut() {
		return out;
	}
}
