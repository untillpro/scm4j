package org.scm4j.commons.progress;

import java.io.PrintStream;
import com.google.common.base.Strings;

import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

public class ProgressConsole implements IProgress {

	private int level;
	private String name;
	private String indent;
	private String outdent;
	private PrintStream out;

	public int getLevel() {
		return level;
	}

	public String getName() {
		return name;
	}

	public String getIndent() {
		return indent;
	}

	public String getOutdent() {
		return outdent;
	}

	public PrintStream getOut() {
		return out;
	}

	public ProgressConsole(String name, String indent, String outdent) {
		this(System.out, 0, name, indent, outdent);
	}

	public ProgressConsole(String name) {
		this(System.out, 0, name, "", "");
	}
	
	public ProgressConsole() {
		this(System.out, 0, "", "", "");
	}

	public ProgressConsole(PrintStream out, int level, String name, String indent, String outdent) {
		this.out = out;
		this.name = name;
		this.level = level;
		this.indent = indent;
		this.outdent = outdent;
		indent(level);
		if (!(indent + name).isEmpty()) {
			out.println(indent + name);
		}
	}

	protected void indent(int level) {
		out.print(Strings.repeat("\t", level));
	}

	@Override
	public IProgress createNestedProgress(String name) {
		return new ProgressConsole(out, level + 1, name, indent, outdent);
	}
	
	@Override
	public void reportStatus(String status) {
		indent(level + 1);
		out.println(status);
	}

	@Override
	public void close() {
		level--;
		if (!(outdent + name).isEmpty()) {
			reportStatus(outdent + name);
		}
	}

	public void trace(String message) {
		
	}

	@Override
	public void error(String message) {
		reportStatus(ansi().fg(RED).a(message).reset().toString());
	}

	@Override
	public IProgress startTrace(String startMessage, String endMessage) {
		return new SingleLineProgressConsole(level + 1, out, startMessage, endMessage);
	}
}
