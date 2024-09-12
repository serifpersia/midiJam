package midiJam;

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

public class MidiJamServerCli {

	private static final int MAX_ID = 9999;
	private static final String PORT_FILE_NAME = "port.config";
	private DatagramSocket serverSocket;
	private Thread serverThread;
	private Set<Integer> assignedIds = new HashSet<>();
	private Map<Integer, ClientInfo> connectedClients = new HashMap<>();
	private volatile boolean running = true;

	public static void main(String[] args) {
		MidiJamServerCli server = new MidiJamServerCli();
		server.startServer(args);
	}

	void startServer(String[] args) {
		int port = loadPortFromFile();
		for (int i = 0; i < args.length; i++) {
			if ("-port".equals(args[i]) && i + 1 < args.length) {
				try {
					port = Integer.parseInt(args[i + 1]);
					System.out.println("Port provided from arguments: " + port);
					savePortToFile(port);
					break;
				} catch (NumberFormatException e) {
					System.err.println("Invalid port number in arguments. Using the file/default port.");
				}
			}
		}

		try {
			serverSocket = new DatagramSocket(port);
			System.out.println("Server running at IP: " + InetAddress.getLocalHost().getHostAddress() + ", Port: "
					+ serverSocket.getLocalPort());
			startServerThread();

			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

			while (running) {
				Thread.sleep(1000);
			}

		} catch (Exception e) {
			System.err.println("Failed to start server: " + e.getMessage());
		}
	}

	private int loadPortFromFile() {
		File file = new File(PORT_FILE_NAME);
		int defaultPort = 5000;
		if (file.exists()) {
			try (Scanner fileScanner = new Scanner(file)) {
				if (fileScanner.hasNextInt()) {
					int port = fileScanner.nextInt();
					System.out.println("Port loaded from file: " + port);
					return port;
				} else {
					System.out.println("Port file is empty or invalid. Using default port: " + defaultPort);
				}
			} catch (IOException e) {
				System.err.println("Error reading port from file. Using default port: " + defaultPort);
			}
		} else {
			System.out.println("Port file not found. Using default port: " + defaultPort);
			savePortToFile(defaultPort);
		}
		return defaultPort;
	}

	private void savePortToFile(int port) {
		try (PrintWriter writer = new PrintWriter(PORT_FILE_NAME)) {
			writer.println(port);
			System.out.println("Port saved to file: " + port);
		} catch (IOException e) {
			System.err.println("Failed to save port to file: " + e.getMessage());
		}
	}

	private void startServerThread() {
		serverThread = new Thread(() -> {
			byte[] buffer = new byte[1024];
			while (running && !serverSocket.isClosed()) {
				handleClientRequest(buffer);
			}
		});
		serverThread.start();
	}

	private void handleClientRequest(byte[] buffer) {
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
					System.out.println("Invalid CONNECT message format.");
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
			} else {
				System.out.println("Unknown message type: " + message);
			}
		} catch (IOException e) {
			if (running && !serverSocket.isClosed()) {
				System.err.println("Error: " + e.getMessage());
			}
		}
	}

	private void handleConnectMessage(InetAddress clientAddress, int clientPort, String clientName) throws IOException {
		int clientId = assignClientId();
		connectedClients.put(clientId, new ClientInfo(clientId, clientAddress, clientPort, clientName));
		System.out.println("Client: " + clientId + " (" + clientName + ") connected");

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
			System.out.println("Client: " + clientId + " disconnected");

			broadcastClientCount();
			broadcastClientList();
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			System.out.println("Invalid disconnect message format: " + e.getMessage());
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

			System.out.printf("CHORD_KEYS from %s (ID: %d): Note=%d, isNoteOn=%b, Chord=%s%n", clientName, clientId,
					note, isNoteOn, chordName);

			forwardMessageToClients(message, clientId);
		} else {
			System.out.println("Invalid CHORD_KEYS message format.");
		}
	}

	private void handleTextMessage(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length == 4) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			String actualMessage = parts[3];

			System.out.println("TEXT Message from " + clientName + ": " + actualMessage);

			forwardMessageToClients("TEXT:" + clientId + ":" + clientName + ":" + actualMessage, clientId);
		} else {
			System.out.println("Invalid TEXT message format.");
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
				System.err.println("Error parsing MIDI data from client " + clientName + ": " + e.getMessage());
				return;
			}

			System.out.printf("MIDI from %s: Status=%d, Channel=%d, Data1=%d, Data2=%d%n", clientName, status, channel,
					data1, data2);

			forwardMessageToClients(message, clientId);
		} else {
			System.out.println("Invalid MIDI message format.");
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
				System.out.println("Client " + muterId + " muted client " + mutedId);
			} else {
				System.out.println("Client " + muterId + " not found.");
			}
		} else {
			System.out.println("Invalid MUTE message format.");
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
				System.out.println("Client " + unmuterId + " unmuted client " + unmutedId);
			} else {
				System.out.println("Client " + unmuterId + " not found.");
			}
		} else {
			System.out.println("Invalid UNMUTE message format.");
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
			System.err.println("Failed to forward message to ID: " + client.getId() + ": " + e.getMessage());
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

	private void shutdown() {
		running = false;
		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
		}
		if (serverThread != null) {
			serverThread.interrupt();
			try {
				serverThread.join();
			} catch (InterruptedException e) {
				System.err.println("Server thread interrupted during shutdown: " + e.getMessage());
			}
		}
		System.out.println("Server shutdown complete.");
	}

	private class ClientInfo {
		private int id;
		private InetAddress address;
		private int port;
		private String name;
		private Set<Integer> mutedClients;

		public ClientInfo(int id, InetAddress address, int port, String name) {
			this.id = id;
			this.address = address;
			this.port = port;
			this.name = name;
			this.mutedClients = new HashSet<>();
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
	}
}
