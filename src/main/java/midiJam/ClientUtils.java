package midiJam;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;

import javax.swing.JTextArea;

public class ClientUtils {

	Logger logger;

	private final String HOSTNAME_FILE_NAME = "hostname.config";

	public ClientUtils(boolean isGui, JTextArea statusArea) {
		if (isGui) {
			this.logger = new Logger(statusArea);
		} else {
			this.logger = new Logger();
		}
	}

	private String generateDefaultName() {
		Random random = new Random();
		int randomId = 10000 + random.nextInt(90000);
		return "Guest#" + randomId;
	}

	String[] loadNameAndHostNameFromFile() {
		File file = new File(HOSTNAME_FILE_NAME);
		String defaultName = generateDefaultName();
		String defaultHostName = "127.0.0.1:5000";

		if (file.exists()) {
			try (Scanner fileScanner = new Scanner(file)) {
				if (fileScanner.hasNextLine()) {
					String name = fileScanner.nextLine();
					if (fileScanner.hasNextLine()) {
						String hostName = fileScanner.nextLine();
						logger.log("Name and HostName loaded from file: " + name + " - " + hostName);
						return new String[] { name, hostName };
					}
				}
				logger.log("HostName file is incomplete. Using defaults.");
			} catch (IOException e) {
				System.err.println("Error reading Name and HostName from file. Using defaults.");
			}
		} else {
			logger.log("HostName file not found. Using defaults.");
			saveNameAndHostNameToFile(defaultName, defaultHostName);
		}
		return new String[] { defaultName, defaultHostName };
	}

	void saveNameAndHostNameToFile(String name, String hostName) {
		try (PrintWriter writer = new PrintWriter(HOSTNAME_FILE_NAME)) {
			writer.println(name);
			writer.println(hostName);
			logger.log("Name and HostName saved to file: " + name + " - " + hostName);
		} catch (IOException e) {
			System.err.println("Failed to save Name and HostName to file: " + e.getMessage());
		}
	}

}
