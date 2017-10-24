package org.scm4j.commons.progress;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.io.PrintStream;

import org.fusesource.jansi.Ansi;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.base.Strings;

public class SingleLineProgressConsoleTest {
	
	private static final String END_2 = "end 2";
	private static final String START_2 = "start 2";
	private static final String ERROR = "error";
	private static final String TRACE = "trace";
	private static final String END_MESSAGE = "end message";
	private static final String START_MESSAGE = "startMessage";
	private static final String START_3 = "start 3";

	@Test
	public void testSingleLineTrace() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new SingleLineProgressConsole(mockedOut, START_MESSAGE, END_MESSAGE)) {
			verify(mockedOut).print("");
			verify(mockedOut).print(START_MESSAGE);
			pc.reportStatus(TRACE);
			verify(mockedOut).print(TRACE);
			try (IProgress pcNested = pc.createNestedProgress(START_2)) {
				verify(mockedOut, times(2)).print("");
				verify(mockedOut).print(START_2);
			}
			verify(mockedOut).println(END_MESSAGE);
			
			try (IProgress pcNested = pc.startTrace(START_3, END_2)) {
				verify(mockedOut, times(3)).print("");
				verify(mockedOut).print(START_3);
			}
			verify(mockedOut).println(END_2);
			
		}
		verify(mockedOut, times(2)).println(END_MESSAGE);
	}
	
	@Test
	public void testIndents() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		try (IProgress pc = new SingleLineProgressConsole(3, mockedOut, START_MESSAGE, END_MESSAGE)) {
			Mockito.verify(mockedOut).print(Strings.repeat("\t", 3));
			Mockito.verify(mockedOut).print(START_MESSAGE);
		}
	}
	
	@Test
	public void testError() throws Exception {
		PrintStream mockedOut = Mockito.mock(PrintStream.class);
		Ansi.setEnabled(true);
		try (IProgress pc = new SingleLineProgressConsole(mockedOut, START_MESSAGE, END_MESSAGE)) {
			pc.error(ERROR);
			Mockito.verify(mockedOut).print("");
			String test = ansi().fg(RED).a(ERROR).reset().toString();
			Mockito.verify(mockedOut).print(test);
		}
	}
	
	@Test
	public void testConstructorDefaults() throws Exception {
		SingleLineProgressConsole pc = new SingleLineProgressConsole(START_MESSAGE, END_MESSAGE);
		assertEquals(System.out, pc.getOut());
		assertEquals(END_MESSAGE, pc.getEndMessage());
		pc.close();
	}
}
