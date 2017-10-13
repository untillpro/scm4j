package org.scm4j.commons.progress;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.io.PrintStream;

import org.fusesource.jansi.Ansi;
import org.mockito.Mockito;

import junit.framework.TestCase;

public class ProgressConsoleTest extends TestCase {
	
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
			Mockito.verify(mockedOut).print(">>> Progress 1");
			Mockito.verify(mockedOut).println();
			Mockito.reset(mockedOut);
			try (IProgress nestedPc = pc.createNestedProgress("Progress 2")) {
				Mockito.verify(mockedOut).print("\t");
				Mockito.verify(mockedOut).print(">>> Progress 2");
				Mockito.verify(mockedOut).println();
				Mockito.reset(mockedOut);
			}
			Mockito.verify(mockedOut).print("\t");
			Mockito.verify(mockedOut).print("<<< Progress 2");
			Mockito.verify(mockedOut).println();
			Mockito.reset(mockedOut);
		}
		Mockito.verify(mockedOut).print("");
		Mockito.verify(mockedOut).print("<<< Progress 1");
		Mockito.verify(mockedOut).println();
	}
	
	public void testColoring() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		Ansi.setEnabled(true);
		try (IProgress pc = new ProgressConsole(mockedOut, 0, "Progress 1", ">>> ", "<<< ")) {
			pc.error("error");
			Mockito.verify(mockedOut).print("\t");
			String test = ansi().fg(RED).a("error").reset().toString();
			Mockito.verify(mockedOut).print(test);
			Mockito.verify(mockedOut, Mockito.times(2)).println();
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
		ProgressConsole pc = new ProgressConsole("Progress 1", ">>> ", "<<< ");
		pc.trace("trace");
		pc.close();
	}
}
