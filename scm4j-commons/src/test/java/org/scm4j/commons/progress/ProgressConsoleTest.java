package org.scm4j.commons.progress;

import junit.framework.TestCase;
import org.mockito.Mockito;

import java.io.PrintStream;

public class ProgressConsoleTest extends TestCase {
	
	private static final String TRACE = "trace";
	private static final String END_MESSAGE = "endMessage";
	private static final String START_MESSAGE = "start message";

	public void testMultiline() throws Exception{
		System.out.println("***Multiline:");
		ProgressConsole rpc1 = new ProgressConsole("Progress 1");
		rpc1.reportStatus("Status1.1");
		rpc1.reportStatus("Status1.2");
		{
			IProgress rp2 = rpc1.createNestedProgress("Progress 2");
			rp2.reportStatus("Status2.1");
			rp2.reportStatus("Status2.2");
			{
				try (IProgress rp3 = rp2.createNestedProgress("Progress 3")) {
					rp3.reportStatus("Status3.1");
					rp3.reportStatus("Status3.2");
				}
			}
			rp2.close();
		}
		rpc1.reportStatus("Status1.3");
		rpc1.reportStatus("Status1.4");
		rpc1.close();
	}
	
	public void testPrinting() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new ProgressConsole(mockedOut, 0, "Progress 1", ">>> ", "<<< ")) {
			Mockito.verify(mockedOut).print("");
			Mockito.verify(mockedOut).println(">>> Progress 1");
			Mockito.reset(mockedOut);
			try (IProgress nestedPc = pc.createNestedProgress("Progress 2")) {
				Mockito.verify(mockedOut).print("\t");
				Mockito.verify(mockedOut).println(">>> Progress 2");
				Mockito.reset(mockedOut);
			}
			Mockito.verify(mockedOut).print("\t");
			Mockito.verify(mockedOut).println("<<< Progress 2");
			Mockito.reset(mockedOut);
		}
		Mockito.verify(mockedOut).print("");
		Mockito.verify(mockedOut).println("<<< Progress 1");
	}
	
	public void testError() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new ProgressConsole(mockedOut, 0, "Progress 1", ">>> ", "<<< ")) {
			pc.error("error");
			Mockito.verify(mockedOut).print("\t");
			Mockito.verify(mockedOut).println("error");
		}
	}
	
	public void testConstructor() {
		ProgressConsole pc = Mockito.spy(new ProgressConsole("Progress 1", ">>> ", "<<< "));
		assertEquals(pc.getOut(), System.out);
		assertEquals(pc.getIndent(), ">>> ");
		assertEquals(pc.getName(), "Progress 1");
		assertEquals(pc.getOutdent(), "<<< ");
		assertEquals(pc.getLevel(), 0);
	}

	public void testTrace() {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		ProgressConsole pc = new ProgressConsole(mockedOut, 0, "Progress 1", ">>> ", "<<< ");
		pc.startTrace(START_MESSAGE);
		pc.trace(TRACE);
		pc.endTrace(END_MESSAGE);
		Mockito.verify(mockedOut).print("");
		Mockito.verify(mockedOut).println(">>> Progress 1");
		Mockito.verify(mockedOut).print("\t");
		Mockito.verify(mockedOut).print(START_MESSAGE);
		Mockito.verify(mockedOut).print(TRACE);
		Mockito.verify(mockedOut).println(END_MESSAGE);
		pc.close();
	}
	
	public void testEmpty() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new ProgressConsole(mockedOut, 0, "", "", "")) {
		}
		Mockito.verifyNoMoreInteractions(mockedOut);
		
		ProgressConsole pc = new ProgressConsole();
		assertEquals("", pc.getIndent());
		assertEquals(0, pc.getLevel());
		assertEquals("", pc.getName());
		assertEquals(System.out, pc.getOut());
		assertEquals("", pc.getOutdent());
		pc.close();
	}
	
	public void testNestedTraceFromEmpty() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		IProgress pc = new ProgressConsole(mockedOut, 0, "", "", "");
		try (IProgress pcNested = pc.createNestedProgress("")) {
			pcNested.startTrace(START_MESSAGE);
			pcNested.trace(TRACE);
			pcNested.endTrace(END_MESSAGE);
		}
		Mockito.verify(mockedOut).print("\t");
		Mockito.verify(mockedOut).print(START_MESSAGE);
		Mockito.verify(mockedOut).print(TRACE);
		Mockito.verify(mockedOut).println(END_MESSAGE);
		pc.close();
	}
	
	public void testCRLFIndent() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new ProgressConsole(mockedOut, 0, "", "", "")) {
			try (IProgress pcNested = pc.createNestedProgress("")) {
				pcNested.reportStatus("\r\ntest");
				Mockito.verify(mockedOut).print("\t");
				Mockito.verify(mockedOut).println("\r\n\t\ttest");
			}
		}
	}
	
	public void testReportStatusIfEmpty() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		IProgress pc = new ProgressConsole(mockedOut, 0, "", "", "");
		pc.reportStatus(TRACE);
		Mockito.verify(mockedOut).print(""); // 0 indent
		Mockito.verify(mockedOut).println(TRACE);
		Mockito.verifyNoMoreInteractions(mockedOut);
		pc.close();
	}
}
