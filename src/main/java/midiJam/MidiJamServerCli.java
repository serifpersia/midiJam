package midiJam;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Scanner;

public class MidiJamServerCli {

	private static final int MAX_ID = 9999;
	private DatagramSocket serverSocket;
	private Thread serverThread;
	private Set<Integer> assignedIds = new HashSet<>();
	private Map<Integer, ClientInfo> connectedClients = new HashMap<>();
	private Scanner scanner = new Scanner(System.in);
	private volatile boolean running = true;

	public static void main(String[] args) {
		MidiJamServerCli server = new MidiJamServerCli();
		server.startServer();
	}

	private void startServer() {
		int port = promptForPort();
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

	private int promptForPort() {
		int defaultPort = 5000;
		System.out.print("Enter UDP port (default is 5000): ");
		String portStr = scanner.nextLine();
		if (portStr.isEmpty()) {
			return defaultPort;
		}
		try {
			return Integer.parseInt(portStr);
		} catch (NumberFormatException e) {
			System.err.println("Invalid port number. Please enter a valid integer.");
			System.exit(1);
		}
		return defaultPort;
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
				handleConnectMessage(clientAddress, clientPort);
			} else if (message.startsWith("DISCONNECT:")) {
				handleDisconnectMessage(message);
			} else if (message.startsWith("TEXT:")) {
				handleTextMessage(message);
			} else if (message.startsWith("MIDI:")) {
				handleMidiMessage(message);
			} else if (message.startsWith("CHORD_KEYS:")) {
				handleChordKeysMessage(message);
			} else {
				System.out.println("Unknown message type: " + message);
			}
		} catch (IOException e) {
			if (running && !serverSocket.isClosed()) {
				System.err.println("Error: " + e.getMessage());
			}
		}
	}

	private void handleConnectMessage(InetAddress clientAddress, int clientPort) throws IOException {
		int clientId = assignClientId();
		connectedClients.put(clientId, new ClientInfo(clientId, clientAddress, clientPort));
		System.out.println("Client: " + clientId + " connected");

		String idMessage = "ID:" + clientId;
		DatagramPacket idPacket = new DatagramPacket(idMessage.getBytes(), idMessage.length(), clientAddress,
				clientPort);
		serverSocket.send(idPacket);

		broadcastClientCount();
	}

	private void handleDisconnectMessage(String message) {
		int clientId = Integer.parseInt(message.substring(11));
		connectedClients.remove(clientId);
		System.out.println("Client: " + clientId + " disconnected");

		broadcastClientCount();
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

	private void forwardMessageToClients(String message, int senderClientId) {
		connectedClients.values().stream().filter(client -> client.getId() != senderClientId)
				.forEach(client -> sendPacketToClient(message, client));
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

		public ClientInfo(int id, InetAddress address, int port) {
			this.id = id;
			this.address = address;
			this.port = port;
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
	}
}
