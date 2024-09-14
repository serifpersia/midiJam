package midiJam;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;

import java.util.function.Consumer;

public class Logger {
	private Consumer<String> logHandler;
	private static final int MAX_LOG_LINES = 25;

	public Logger() {
		this.logHandler = System.out::println;
	}

	public Logger(JTextArea statusArea) {
		this.logHandler = message -> SwingUtilities.invokeLater(() -> {
			statusArea.append(message + "\n");
			limitLogSize(statusArea);
		});
	}

	public void log(String message) {
		logHandler.accept(message);
	}

	public void limitLogSize(JTextArea statusArea) {
		int lineCount = statusArea.getLineCount();
		if (lineCount > MAX_LOG_LINES) {
			Document doc = statusArea.getDocument();
			try {
				int endOffset = statusArea.getLineEndOffset(lineCount - MAX_LOG_LINES);
				doc.remove(0, endOffset);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	void limitLogSize(StyledDocument doc) {
		try {
			String text = doc.getText(0, doc.getLength());

			String[] lines = text.split("\n");

			if (lines.length > MAX_LOG_LINES) {
				int linesToRemove = lines.length - MAX_LOG_LINES;
				int startOffset = 0;
				int endOffset = 0;
				for (int i = 0; i < linesToRemove; i++) {
					startOffset = text.indexOf(lines[i] + "\n");
					if (startOffset == -1) {
						startOffset = text.indexOf(lines[i]);
					}
					endOffset = startOffset + lines[i].length()
							+ (startOffset + lines[i].length() + 1 < text.length() ? 1 : 0);
				}

				doc.remove(0, endOffset);
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}
}
