package midiJam;

import java.awt.Color;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class MidiJamClientNetworking {

	private ClientUtils clientUtils;
	private MidiJamClientGUI gui;

	DatagramSocket clientSocket;
	private InetAddress serverAddress;

	private int serverPort;
	static int clientId;
	static String clientName;
	private Map<String, String> currentClients = new HashMap<>();

	private static final int PING_INTERVAL_MS = 10000;
	private Timer connectivityTimer;
	private boolean isConnected = false;

	public MidiJamClientNetworking(ClientUtils clientUtils, MidiJamClientGUI gui) {
		this.clientUtils = clientUtils;
		this.gui = gui;
	}

	void startConnectivityCheck() {
		connectivityTimer = new Timer(true);
		connectivityTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkNetworkConnection();
			}
		}, 0, PING_INTERVAL_MS);
	}

	private void checkNetworkConnection() {
		try {
			if (serverAddress != null && serverAddress.isReachable(2000)) {
				if (!isConnected) {
					isConnected = true;
					clientUtils.logger.log("Connection to server restored.");
				}
			} else {
				if (isConnected) {
					clientUtils.logger.log("Connection to server lost. Disconnecting...");
					disconnectClient();
					isConnected = false;
				}
			}
		} catch (IOException e) {
			if (isConnected) {
				clientUtils.logger.log("Error checking connection to server: " + e.getMessage());
				disconnectClient();
				isConnected = false;
			}
		}
	}

	void connectClient() {
		try {
			gui.tglConnect.setEnabled(false);
			startClient();
			startConnectivityCheck();
		} catch (Exception ex) {
			clientUtils.logger.log("Failed to connect: " + ex.getMessage());
		}
	}

	void disconnectClient() {
		try {
			if (connectivityTimer != null) {
				connectivityTimer.cancel();
			}
			closeClient();
		} catch (Exception ex) {
			clientUtils.logger.log("Failed to disconnect: " + ex.getMessage());
		}
	}

	void sendPacket(byte[] data) {
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
			clientSocket.send(packet);
		} catch (IOException e) {
			clientUtils.logger.log("Failed to send packet: " + e.getMessage());
		}
	}

	void sendMessage() {
		String message = gui.messageField.getText().trim();
		if (message.isEmpty()) {
			return;
		}

		if (clientSocket == null || clientSocket.isClosed()) {
			clientUtils.logger.log("Client socket is not available.");
			return;
		}

		String fullMessage = String.format("TEXT:%s:%s:%s", midiJam.MidiJamClientNetworking.clientId,
				midiJam.MidiJamClientNetworking.clientName, message);
		try {
			sendPacket(fullMessage.getBytes());

			SwingUtilities.invokeLater(() -> {
				gui.appendColoredStatus(String.format("%s: %s", midiJam.MidiJamClientNetworking.clientName, message),
						Color.RED);
				gui.messageField.setText("");
			});
		} catch (Exception e) {
			SwingUtilities.invokeLater(() -> clientUtils.logger.log("Failed to send message: " + e.getMessage()));
		}
	}

	private void handleReceivedMessage(String message) {
		if (message.startsWith("TEXT:")) {
			handleTextMessage(message);
		} else if (message.startsWith("MIDI:")) {
			handleMidiMessage(message);
		} else if (message.startsWith("CHORD_KEYS:")) {
			handleChordKeys(message);
		} else if (message.startsWith("COUNT:")) {
			int count = Integer.parseInt(message.substring(6));
			updateSessionLabel(count);
		} else if (message.startsWith("CLIENT_LIST:")) {
			handleClientListMessage(message);
		} else if (message.startsWith("MUTE:")) {
			handleMuteMessage(message);
		} else if (message.startsWith("UNMUTE:")) {
			handleUnmuteMessage(message);
		} else if (message.startsWith("PING:")) {
			handlePingRequest(message);
		} else if (message.startsWith("PING_INFO:")) {
			handlePingInfoMessage(message);
		} else if (message.equals("SERVER_SHUTDOWN")) {
			handleServerShutdown();
		} else {
			clientUtils.logger.log("Unknown message type: " + message);
		}
	}

	private void handleServerShutdown() {
		clientUtils.logger.log("Received SERVER_SHUTDOWN message. Disconnecting...");
		disconnectClient();
	}

	private void handlePingRequest(String message) {
		String[] parts = message.split(":");
		long sentTime = Long.parseLong(parts[1]);
		String pingResponseMessage = "PING_RESPONSE:" + sentTime;

		sendPacket(pingResponseMessage.getBytes());

	}

	private void handlePingInfoMessage(String message) {
		String[] parts = message.split(":");
		int clientId = Integer.parseInt(parts[1]);
		String ping = parts[3];

		if (gui.clientLabels.containsKey(clientId)) {
			JLabel clientLabel = gui.clientLabels.get(clientId);
			String currentText = clientLabel.getText();

			String updatedText = currentText.split("Ping:")[0].trim() + " Ping: " + ping;
			clientLabel.setText(updatedText);

			gui.clientPingTimestamps.put(clientId, System.currentTimeMillis());
		}

	}

	private void updateSessionLabel(int count) {
		gui.lb_inSessionCount.setText("In Session: " + count);
	}

	private void handleTextMessage(String message) {
		String[] parts = message.split(":", 4);

		if (parts.length != 4) {
			clientUtils.logger.log("Invalid TEXT message format.");
			return;
		}

		int otherClientId;
		String otherClientName;
		String actualMessage;
		Color clientColor;

		try {
			otherClientId = Integer.parseInt(parts[1]);
			otherClientName = parts[2];
			actualMessage = parts[3];
			clientColor = gui.getColorForClientId(otherClientId);
		} catch (NumberFormatException e) {
			clientUtils.logger.log("Error parsing client ID: " + e.getMessage());
			return;
		}

		gui.appendColoredStatus(String.format("%s: %s", otherClientName, actualMessage), clientColor);
	}

	private void handleMidiMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 7) {
			int senderClientId = Integer.parseInt(parts[1]);
			String senderName = parts[2];
			int status, channel, data1, data2;

			if (gui.mutedClients.contains(senderClientId)) {
				return;
			}
			try {
				status = Integer.parseInt(parts[3]);
				channel = Integer.parseInt(parts[4]);
				data1 = Integer.parseInt(parts[5]);
				data2 = Integer.parseInt(parts[6]);
			} catch (NumberFormatException e) {
				SwingUtilities.invokeLater(() -> clientUtils.logger.log("Error parsing MIDI data."));
				return;
			}
			SwingUtilities.invokeLater(() -> gui.activeSenderlb.setText("Active Client: " + senderName));
			gui.clientMidiActivityStatePanel.setState(true);
			gui.sendToMidiDevice(status, channel, data1, data2);
		} else {
		}
	}

	private void handleChordKeys(String message) {

		String[] parts = message.split(":", 6);
		if (parts.length == 6) {
			String clientName = parts[2];
			int pitch = Integer.parseInt(parts[3]);
			boolean isNoteOn = Boolean.parseBoolean(parts[4]);
			String chordName = parts[5];

			SwingUtilities
					.invokeLater(() -> MidiJamClientGUI.chordPanelInstance.updateChordLabel(clientName, chordName));

			SwingUtilities.invokeLater(() -> {
				if (isNoteOn) {
					MidiJamClientGUI.chordPanelInstance.piano.setPianoKey(pitch, 1);
				} else {
					MidiJamClientGUI.chordPanelInstance.piano.setPianoKey(pitch, 0);
				}
			});
		}
	}

	private void handleClientListMessage(String message) {
		String clientList = message.substring(12);

		String[] clientArray = clientList.split(",");

		Map<String, String> newClients = new HashMap<>();

		for (String clientInfo : clientArray) {
			clientInfo = clientInfo.trim();

			int colonIndex = clientInfo.indexOf(':');

			if (colonIndex != -1) {
				String id = clientInfo.substring(0, colonIndex);
				String name = clientInfo.substring(colonIndex + 1);

				newClients.put(id, name);
			}
		}

		Set<String> addedClients = new HashSet<>(newClients.keySet());
		addedClients.removeAll(currentClients.keySet());

		Set<String> removedClients = new HashSet<>(currentClients.keySet());
		removedClients.removeAll(newClients.keySet());

		gui.updateClientRows(newClients, addedClients, removedClients);

		currentClients = newClients;
	}

	private void handleMuteMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 3) {
			int clientId = Integer.parseInt(parts[1]);
			int clientIdToMute = Integer.parseInt(parts[2]);

			if (clientId == MidiJamClientNetworking.clientId) {
				gui.mutedClients.add(clientIdToMute);
			}
		}
	}

	private void handleUnmuteMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 3) {
			int clientId = Integer.parseInt(parts[1]);
			int clientIdToUnmute = Integer.parseInt(parts[2]);

			if (clientId == MidiJamClientNetworking.clientId) {
				gui.mutedClients.remove(clientIdToUnmute);
			}
		} else {
			clientUtils.logger.log("Invalid UNMUTE message format.");
		}
	}

	private void receiveMessages() {
		new Thread(() -> {
			byte[] buffer = new byte[256];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			while (clientSocket != null && !clientSocket.isClosed()) {
				try {
					clientSocket.receive(packet);
					String message = new String(packet.getData(), 0, packet.getLength()).trim();
					handleReceivedMessage(message);
				} catch (Exception e) {
				}
			}
		}).start();
	}

	private void connectToServer() throws IOException {
		String connectMessage = "CONNECT:" + clientName;
		sendPacket(connectMessage.getBytes());

		byte[] buffer = new byte[256];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		clientSocket.setSoTimeout(2000);

		try {
			clientSocket.receive(packet);
			String idMessage = new String(packet.getData(), 0, packet.getLength());

			if (idMessage.startsWith("ID:")) {
				clientId = Integer.parseInt(idMessage.substring(3));
				gui.appendStatus("Connected to server with ID: " + clientId);
				gui.tglConnect.setText("Disconnect");
				gui.tglConnect.setSelected(false);
				gui.tglConnect.setEnabled(true);

				gui.startMidiRouting();
				receiveMessages();
				gui.clientConnectionStatePanel.setState(true);
			} else {
				gui.showErrorDialog("Failed to receive ID from server.");
				gui.tglConnect.setText("Connect");
				gui.tglConnect.setSelected(false);
				gui.tglConnect.setEnabled(true);
			}
		} catch (SocketTimeoutException ste) {
			gui.showErrorDialog("Connection to server timed out. Please try again.");
			gui.tglConnect.setText("Connect");
			gui.tglConnect.setSelected(false);
			gui.tglConnect.setEnabled(true);
		}
	}

	private void startClient() {
		try {
			clientSocket = new DatagramSocket();
			clientUtils.loadNameAndHostNameFromFile();
			ClientSetup clientSetup = gui.promptClientSetup();

			if (clientSetup != null) {
				serverAddress = clientSetup.serverAddress;
				serverPort = clientSetup.serverPort;
				clientName = clientSetup.clientName;

				connectToServer();
			} else {
				gui.tglConnect.setSelected(false);
				gui.tglConnect.setEnabled(true);
			}
		} catch (Exception e) {
			gui.showErrorDialog("Failed to connect to server: " + e.getMessage());

		}
	}

	void closeClient() {
		try {
			gui.stopMidiRouting();
			gui.saveConfiguration(gui.inputDeviceDropdown.getSelectedIndex(),
					gui.outputDeviceDropdown.getSelectedIndex(), gui.midi_ch_list_dropdown.getSelectedIndex());
			if (clientSocket != null && !clientSocket.isClosed()) {
				String disconnectMessage = "DISCONNECT:" + clientId;
				sendPacket(disconnectMessage.getBytes());
				clientSocket.close();
				gui.clientConnectionStatePanel.setState(false);
				gui.appendStatus("Disconnected from server.");
			}
		} catch (Exception e) {
			clientUtils.logger.log("Error during disconnection: " + e.getMessage());
		} finally {
			gui.tglConnect.setText("Connect");
		}
	}

	static class ClientSetup {
		String clientName;
		InetAddress serverAddress;
		int serverPort;

		ClientSetup(String clientName, InetAddress serverAddress, int serverPort) {
			this.clientName = clientName;
			this.serverAddress = serverAddress;
			this.serverPort = serverPort;
		}
	}

}
