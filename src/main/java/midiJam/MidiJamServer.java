package midiJam;

import java.awt.*;
import java.net.*;
import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@SuppressWarnings("serial")
public class MidiJamServer extends JFrame {

	private static final int MAX_ID = 9999;
	private static final String PORT_FILE_NAME = "port.config";
	private DatagramSocket serverSocket;
	private Thread serverThread;

	private Set<Integer> assignedIds = new HashSet<>();
	private Map<Integer, ClientInfo> connectedClients;

	private static JTextArea statusArea;

	public static void main(String[] args) {
		if (args.length > 0 && "--nogui".equals(args[0])) {
			new MidiJamServerCli();
			MidiJamServerCli server = new MidiJamServerCli();
			server.startServer(args);
		} else {
			EventQueue.invokeLater(() -> {
				try {
					UIManager.setLookAndFeel(new FlatDarkLaf());
					MidiJamServer frame = new MidiJamServer();
					frame.setVisible(true);
				} catch (UnsupportedLookAndFeelException e) {
					e.printStackTrace();
				}
			});
		}
	}

	public MidiJamServer() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(300, 200);
		setTitle("midiJam Server v1.0.5");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());
		setResizable(false);

		connectedClients = new HashMap<>();

		initComponents();
		setLocationRelativeTo(null);

		startServer();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closeServer();
			}
		});
	}

	private void initComponents() {
		getContentPane().setLayout(new BorderLayout());

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel chatPanel = new JPanel();
		tabbedPane.addTab("Chat", null, chatPanel, null);
		chatPanel.setLayout(new BorderLayout(0, 0));

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));

		chatPanel.add(statusScrollPane, BorderLayout.CENTER);
	}

	private int promptForPort() {
		int port = loadPortFromFile();

		String portStr = JOptionPane.showInputDialog(this, "Enter UDP port (default is 5000):", port);

		if (portStr == null) {
			System.exit(0);
		}

		try {
			port = Integer.parseInt(portStr);
			savePortToFile(port);
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid port number. Please enter a valid integer.", "Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		return port;
	}

	private void startServer() {
		int port = promptForPort();
		try {
			serverSocket = new DatagramSocket(port);
			appendStatus("Server running at IP: " + InetAddress.getLocalHost().getHostAddress() + ", Port: "
					+ serverSocket.getLocalPort());
			startServerThread();
		} catch (Exception e) {
			appendStatus("Failed to start server: " + e.getMessage());
		}
	}

	private void startServerThread() {
		serverThread = new Thread(() -> {
			byte[] buffer = new byte[1024];
			while (!serverSocket.isClosed()) {
				handleClientRequest(buffer);
			}
		});
		serverThread.start();
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
					appendStatus("Invalid CONNECT message format.");
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
				appendStatus("Unknown message type: " + message);
			}
		} catch (Exception e) {
			if (!serverSocket.isClosed()) {
				appendStatus("Error: " + e.getMessage());
			}
		}
	}

	private void handleConnectMessage(InetAddress clientAddress, int clientPort, String clientName) throws IOException {
		int clientId = assignClientId();
		connectedClients.put(clientId, new ClientInfo(clientId, clientAddress, clientPort, clientName));
		appendStatus("Client: " + clientId + " (" + clientName + ") connected");

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
			appendStatus("Client: " + clientId + " disconnected");

			broadcastClientCount();
			broadcastClientList();
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			appendStatus("Invalid disconnect message format: " + e.getMessage());
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

			appendStatus(String.format("CHORD_KEYS from %s (ID: %d): Note=%d, isNoteOn=%b, Chord=%s", clientName,
					clientId, note, isNoteOn, chordName));

			forwardMessageToClients(message, clientId);
		} else {
			appendStatus("Invalid CHORD_KEYS message format.");
		}
	}

	private void handleTextMessage(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length == 4) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			String actualMessage = parts[3];

			appendStatus("TEXT Message from " + clientName + ": " + actualMessage);

			forwardMessageToClients("TEXT:" + clientId + ":" + clientName + ":" + actualMessage, clientId);
		} else {
			appendStatus("Invalid TEXT message format.");
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
				appendStatus("Error parsing MIDI data from client " + clientName + ": " + e.getMessage());
				return;
			}

			appendStatus(String.format("MIDI from %s: Status=%d, Channel=%d, Data1=%d, Data2=%d", clientName, status,
					channel, data1, data2));

			forwardMessageToClients(message, clientId);
		} else {
			appendStatus("Invalid MIDI message format.");
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
				appendStatus("Client " + muterId + " muted client " + mutedId);
			} else {
				appendStatus("Client " + muterId + " not found.");
			}
		} else {
			appendStatus("Invalid MUTE message format.");
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
				appendStatus("Client " + unmuterId + " unmuted client " + unmutedId);
			} else {
				appendStatus("Client " + unmuterId + " not found.");
			}
		} else {
			appendStatus("Invalid UNMUTE message format.");
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
			appendStatus("Failed to forward message to ID: " + client.getId() + ": " + e.getMessage());
		}
	}

	private void closeServer() {
		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
			appendStatus("Server socket closed.");
		}
	}

	private void appendStatus(String message) {
		SwingUtilities.invokeLater(() -> statusArea.append(message + "\n"));
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
