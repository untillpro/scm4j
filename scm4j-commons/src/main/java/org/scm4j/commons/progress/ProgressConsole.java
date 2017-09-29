package org.scm4j.commons.progress;

import java.io.PrintStream;

import com.google.common.base.Strings;

public class ProgressConsole implements IProgress {

	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_RESET = "\u001B[0m";

	private int level;
	private String name;
	private String indent;

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

	private String outdent;
	private PrintStream out;

	public PrintStream getOut() {
		return out;
	}

	public ProgressConsole(String name, String indent, String outdent) {
		this(System.out, 0, name, indent, outdent);
	}

	public ProgressConsole(String name) {
		this(System.out, 0, name, "", "");
	}

	public ProgressConsole(PrintStream out, int level, String name, String indent, String outdent) {
		this.out = out;
		this.name = name;
		this.level = level;
		this.indent = indent;
		this.outdent = outdent;
		indent(level);
		print(indent + name);
	}

	protected void print(Object s) {
		out.print(s.toString());
		out.println();
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
		print(status.replace("\r\n", "\r\n" + Strings.repeat("\t", level + 2)));
	}

	@Override
	public void close() {
		level--;
		reportStatus(outdent + name);
	}

	@Override
	public void trace(String message) {

	}

	@Override
	public void error(String message) {
		indent(level + 1);
		print(ANSI_RED + message + ANSI_RESET);
	}
}
