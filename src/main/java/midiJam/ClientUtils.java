package midiJam;

import javax.swing.JTextArea;

public class ClientUtils {

	Logger logger;

	public ClientUtils(boolean isGui, JTextArea statusArea) {
		if (isGui) {
			this.logger = new Logger(statusArea);
		} else {
			this.logger = new Logger();
		}
	}

}
