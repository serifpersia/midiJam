package midiJam;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import java.util.function.Consumer;

public class Logger {
	private Consumer<String> logHandler;

	public Logger() {
		this.logHandler = System.out::println;
	}

	public Logger(JTextArea statusArea) {
		this.logHandler = message -> SwingUtilities.invokeLater(() -> statusArea.append(message + "\n"));
	}

	public void log(String message) {
		logHandler.accept(message);
	}
}
