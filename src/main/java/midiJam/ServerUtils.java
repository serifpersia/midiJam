package midiJam;

import javax.swing.JTextArea;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class ServerUtils {
	private static final int MAX_ID = 9999;
	final static String PORT_FILE_NAME = "port.config";
	DatagramSocket serverSocket;
	Thread serverThread;
	private Set<Integer> assignedIds = new HashSet<>();
	Map<Integer, ClientInfo> connectedClients = new HashMap<>();
	private Timer pingTimer;
	private static final long CLIENT_TIMEOUT_MS = 10000;

	Logger logger;

	public ServerUtils(boolean isGui, JTextArea statusArea) {
		if (isGui) {
			this.logger = new Logger(statusArea);
		} else {
			this.logger = new Logger();
		}
	}

	int loadPortFromFile() {
		File file = new File(ServerUtils.PORT_FILE_NAME);
		int defaultPort = 5000;
		if (file.exists()) {
			try (Scanner fileScanner = new Scanner(file)) {
				if (fileScanner.hasNextInt()) {
					int port = fileScanner.nextInt();
					logger.log("Port loaded from file: " + port);
					return port;
				} else {
					logger.log("Port file is empty or invalid. Using default port: " + defaultPort);
				}
			} catch (IOException e) {
				logger.log("Error reading port from file. Using default port: " + defaultPort);
			}
		} else {
			logger.log("Port file not found. Using default port: " + defaultPort);
			savePortToFile(defaultPort);
		}
		return defaultPort;
	}

	void savePortToFile(int port) {
		try (PrintWriter writer = new PrintWriter(ServerUtils.PORT_FILE_NAME)) {
			writer.println(port);
			logger.log("Port saved to file: " + port);
		} catch (IOException e) {
			logger.log("Failed to save port to file: " + e.getMessage());
		}
	}

	void startServerThread() {
		serverThread = new Thread(() -> {
			byte[] buffer = new byte[512];
			while (!serverSocket.isClosed()) {
				handleClientRequest(buffer);
			}
		});
		serverThread.start();
	}

	void closeServer() {
		if (serverSocket != null && !serverSocket.isClosed()) {

			broadcastServerShutdown();

			serverSocket.close();
			logger.log("Server socket closed.");
		}
	}

	private void broadcastServerShutdown() {
		String shutdownMessage = "SERVER_SHUTDOWN";

		for (ClientInfo client : connectedClients.values()) {
			sendPacketToClient(shutdownMessage, client);
		}

		logger.log("All clients have been notified of server shutdown.");

		connectedClients.clear();
	}

	void handleClientRequest(byte[] buffer) {
		try {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			serverSocket.receive(packet);
			String message = new String(packet.getData(), 0, packet.getLength()).trim();
			InetAddress clientAddress = packet.getAddress();
			int clientPort = packet.getPort();

			if (message.startsWith("CONNECT:")) {
				String[] parts = message.split(":", 2);
				if (parts.length == 2) {
					String clientName = parts[1];
					handleConnectMessage(clientAddress, clientPort, clientName);
				} else {
					logger.log("Invalid CONNECT message format.");
				}
			} else if (message.startsWith("DISCONNECT:")) {
				handleDisconnectMessage(message);
			} else if (message.startsWith("TEXT:")) {
				handleTextMessage(message);
			} else if (message.startsWith("MIDI:")) {
				handleMidiMessage(message);
			} else if (message.startsWith("CHORD_KEYS:")) {
				handleChordKeysMessage(message);
			} else if (message.startsWith("MUTE:")) {
				handleMuteMessage(message);
			} else if (message.startsWith("UNMUTE:")) {
				handleUnmuteMessage(message);
			} else if (message.startsWith("PING_RESPONSE:")) {
				handlePingResponse(message, packet);
			} else {
				logger.log("Unknown message type: " + message);
			}
		} catch (IOException e) {
			if (!serverSocket.isClosed()) {
				logger.log("Error: " + e.getMessage());
			}
		}
	}

	private void handlePingResponse(String message, DatagramPacket packet) {
		String[] parts = message.split(":");
		long sentTime = Long.parseLong(parts[1]);
		long roundTripTime = System.currentTimeMillis() - sentTime;

		InetAddress clientAddress = packet.getAddress();
		int clientPort = packet.getPort();

		for (ClientInfo client : connectedClients.values()) {
			if (client.getAddress().equals(clientAddress) && client.getPort() == clientPort) {

				client.setLastPingTime(System.currentTimeMillis());

				logger.log("Client " + client.getName() + " Ping: " + roundTripTime + "ms");

				sendPingToAllClients(client.getId(), client.getName(), roundTripTime);
				return;
			}
		}
	}

	private void sendPingToAllClients(int clientId, String clientName, long ping) {
		String pingMessage = "PING_INFO:" + clientId + ":" + clientName + ":" + ping + "ms";
		for (ClientInfo client : connectedClients.values()) {
			sendPacketToClient(pingMessage, client);
		}
	}

	void startPingTimer() {
		pingTimer = new Timer(true);
		pingTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				pingClients();
				checkForInactiveClients();
			}
		}, 0, 5000);

	}

	private void checkForInactiveClients() {
		long currentTime = System.currentTimeMillis();

//		logger.log("Checking for inactive clients at time: " + currentTime);

		for (Integer clientId : connectedClients.keySet()) {
			ClientInfo client = connectedClients.get(clientId);
			long lastPingTime = client.getLastPingTime();
			long timeSinceLastPing = currentTime - lastPingTime;

//			logger.log("Client " + clientId + " - Last Ping Time: " + lastPingTime);
//			logger.log("Client " + clientId + " - Time Since Last Ping: " + timeSinceLastPing + "ms");
//			logger.log("Client " + clientId + " - Timeout Threshold: " + CLIENT_TIMEOUT_MS + "ms");

			if (timeSinceLastPing > CLIENT_TIMEOUT_MS) {
//				logger.log("Client " + clientId + " has been disconnected due to inactivity. Time Since Last Ping: "
//						+ timeSinceLastPing + "ms");
				handleDisconnectClient(clientId);
			}
		}
	}

	private void handleDisconnectClient(int clientId) {
		connectedClients.remove(clientId);
		logger.log("Client " + clientId + " disconnected due to inactivity.");
		broadcastClientCount();
		broadcastClientList();
	}

	private void pingClients() {
		long currentTime = System.currentTimeMillis();
		for (ClientInfo client : connectedClients.values()) {
			String pingMessage = "PING:" + currentTime;
			sendPacketToClient(pingMessage, client);
		}
	}

	private void handleConnectMessage(InetAddress clientAddress, int clientPort, String clientName) throws IOException {
		int clientId = assignClientId();
		connectedClients.put(clientId, new ClientInfo(clientId, clientAddress, clientPort, clientName));
		logger.log("Client: " + clientId + " (" + clientName + ") connected");

		String idMessage = "ID:" + clientId;
		DatagramPacket idPacket = new DatagramPacket(idMessage.getBytes(), idMessage.length(), clientAddress,
				clientPort);
		serverSocket.send(idPacket);

		broadcastClientCount();
		broadcastClientList();
	}

	private void handleDisconnectMessage(String message) {
		try {
			int clientId = Integer.parseInt(message.substring(11));
			connectedClients.remove(clientId);
			logger.log("Client: " + clientId + " disconnected");

			broadcastClientCount();
			broadcastClientList();
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			logger.log("Invalid disconnect message format: " + e.getMessage());
		}
	}

	private void handleChordKeysMessage(String message) {
		String[] parts = message.split(":", 6);
		if (parts.length == 6) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			int note = Integer.parseInt(parts[3]);
			boolean isNoteOn = Boolean.parseBoolean(parts[4]);
			String chordName = parts[5];

			logger.log(String.format("CHORD_KEYS from %s (ID: %d): Note=%d, isNoteOn=%b, Chord=%s", clientName,
					clientId, note, isNoteOn, chordName));

			forwardMessageToClients(message, clientId);
		} else {
			logger.log("Invalid CHORD_KEYS message format.");
		}
	}

	private void handleTextMessage(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length == 4) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			String actualMessage = parts[3];

			logger.log("TEXT Message from " + clientName + ": " + actualMessage);

			forwardMessageToClients("TEXT:" + clientId + ":" + clientName + ":" + actualMessage, clientId);
		} else {
			logger.log("Invalid TEXT message format.");
		}
	}

	private void handleMidiMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 7) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			int status, channel, data1, data2;

			try {
				status = Integer.parseInt(parts[3]);
				channel = Integer.parseInt(parts[4]);
				data1 = Integer.parseInt(parts[5]);
				data2 = Integer.parseInt(parts[6]);
			} catch (NumberFormatException e) {
				logger.log("Error parsing MIDI data from client " + clientName + ": " + e.getMessage());
				return;
			}

			logger.log(String.format("MIDI from %s: Status=%d, Channel=%d, Data1=%d, Data2=%d", clientName, status,
					channel, data1, data2));

			forwardMessageToClients(message, clientId);
		} else {
			logger.log("Invalid MIDI message format.");
		}
	}

	private void handleMuteMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 3) {
			int muterId = Integer.parseInt(parts[1]);
			int mutedId = Integer.parseInt(parts[2]);

			ClientInfo muterClient = connectedClients.get(muterId);
			if (muterClient != null) {
				muterClient.addMutedClient(mutedId);
				logger.log("Client " + muterId + " muted client " + mutedId);
			} else {
				logger.log("Client " + muterId + " not found.");
			}
		} else {
			logger.log("Invalid MUTE message format.");
		}
	}

	private void handleUnmuteMessage(String message) {
		String[] parts = message.split(":");
		if (parts.length == 3) {
			int unmuterId = Integer.parseInt(parts[1]);
			int unmutedId = Integer.parseInt(parts[2]);

			ClientInfo unmuterClient = connectedClients.get(unmuterId);
			if (unmuterClient != null) {
				unmuterClient.removeMutedClient(unmutedId);
				logger.log("Client " + unmuterId + " unmuted client " + unmutedId);
			} else {
				logger.log("Client " + unmuterId + " not found.");
			}
		} else {
			logger.log("Invalid UNMUTE message format.");
		}
	}

	private void forwardMessageToClients(String message, int senderClientId) {
		boolean isMutedMessage = message.startsWith("MIDI:") || message.startsWith("CHORD_KEYS:");

		connectedClients.values().stream().filter(client -> client.getId() != senderClientId).forEach(client -> {
			if (isMutedMessage) {
				if (!client.getMutedClients().contains(senderClientId)) {
					sendPacketToClient(message, client);
				}
			} else {
				sendPacketToClient(message, client);
			}
		});
	}

	private void sendPacketToClient(String message, ClientInfo client) {
		try {
			byte[] buffer = message.getBytes();
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client.getAddress(), client.getPort());
			serverSocket.send(packet);
		} catch (Exception e) {
			logger.log("Failed to forward message to ID: " + client.getId() + ": " + e.getMessage());
		}
	}

	private void broadcastClientCount() {
		String countMessage = "COUNT:" + connectedClients.size();
		for (ClientInfo client : connectedClients.values()) {
			sendPacketToClient(countMessage, client);
		}
	}

	private void broadcastClientList() {
		StringBuilder clientListMessage = new StringBuilder("CLIENT_LIST:");
		for (ClientInfo client : connectedClients.values()) {
			clientListMessage.append(client.getId()).append(":").append(client.getName()).append(",");
		}

		String message = clientListMessage.toString();
		for (ClientInfo client : connectedClients.values()) {
			sendPacketToClient(message, client);
		}
	}

	private int assignClientId() {
		for (int id = 0; id < MAX_ID; id++) {
			if (!assignedIds.contains(id)) {
				assignedIds.add(id);
				return id;
			}
		}
		throw new RuntimeException("All client IDs are in use.");
	}

	private class ClientInfo {
		private int id;
		private InetAddress address;
		private int port;
		private String name;
		private Set<Integer> mutedClients;
		private long lastPingTime;

		public ClientInfo(int id, InetAddress address, int port, String name) {
			this.id = id;
			this.address = address;
			this.port = port;
			this.name = name;
			this.mutedClients = new HashSet<>();
			this.lastPingTime = System.currentTimeMillis();
		}

		public int getId() {
			return id;
		}

		public InetAddress getAddress() {
			return address;
		}

		public int getPort() {
			return port;
		}

		public String getName() {
			return name;
		}

		public Set<Integer> getMutedClients() {
			return mutedClients;
		}

		public void addMutedClient(int clientId) {
			mutedClients.add(clientId);
		}

		public void removeMutedClient(int clientId) {
			mutedClients.remove(clientId);
		}

		public long getLastPingTime() {
			return lastPingTime;
		}

		public void setLastPingTime(long lastPingTime) {
			this.lastPingTime = lastPingTime;
		}

	}
}
