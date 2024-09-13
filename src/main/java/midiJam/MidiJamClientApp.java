package midiJam;

import javax.swing.*;

import com.formdev.flatlaf.FlatDarkLaf;

public class MidiJamClientApp {

	static ClientUtils clientUtils = new ClientUtils(false, null);
	static MidiJamClientGUI gui;
	static MidiJamClientNetworking network;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new FlatDarkLaf());
				gui = new MidiJamClientGUI(clientUtils);
				network = new MidiJamClientNetworking(clientUtils, gui);
				gui.setMidiJamClientNetworking(network);
				gui.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}