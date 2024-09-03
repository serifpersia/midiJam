package midiJam;

import java.awt.*;
import java.net.*;
import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("serial")
public class MidiJamServer extends JFrame {

	private static final int MAX_ID = 9999;

	private DatagramSocket serverSocket;
	private Thread serverThread;

	private Set<Integer> assignedIds = new HashSet<>();
	private Map<Integer, ClientInfo> connectedClients;

	private static JTextArea statusArea;

	public static void main(String[] args) {
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

	public MidiJamServer() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(300, 200);
		setTitle("MidiJam Server");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());

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

	private void startServer() {
		try {
			serverSocket = new DatagramSocket(5000);
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
			} else if (message.startsWith("TEXT:") || message.startsWith("MIDI:")) {
				handleTextOrMidiMessage(message);
			} else {
				appendStatus("Unknown message type: " + message);
			}
		} catch (Exception e) {
			if (!serverSocket.isClosed()) {
				appendStatus("Error: " + e.getMessage());
			}
		}
	}

	private void handleConnectMessage(InetAddress clientAddress, int clientPort) throws IOException {
		int clientId = assignClientId();
		connectedClients.put(clientId, new ClientInfo(clientId, clientAddress, clientPort));
		appendStatus("Client: " + clientId + " connected");

		String idMessage = "ID:" + clientId;
		DatagramPacket idPacket = new DatagramPacket(idMessage.getBytes(), idMessage.length(), clientAddress,
				clientPort);
		serverSocket.send(idPacket);

		broadcastClientCount();
	}

	private void handleDisconnectMessage(String message) {
		int clientId = Integer.parseInt(message.substring(11));
		connectedClients.remove(clientId);
		appendStatus("Client: " + clientId + " disconnected");

		broadcastClientCount();
	}

	private void handleTextOrMidiMessage(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length == 4) {
			int clientId = Integer.parseInt(parts[1]);
			String clientName = parts[2];
			String actualMessage = parts[3];
			appendStatus("Client " + clientName + ": " + actualMessage);
			forwardMessageToClients(message, clientId);
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
