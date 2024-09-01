package midiJam;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;

@SuppressWarnings("serial")
public class MidiJam extends JFrame {

	private static final int DEFAULT_PORT = 5000;
	private static final int MAX_ID = 9999;
	private DatagramSocket socket;
	private InetAddress serverAddress;
	private int port = DEFAULT_PORT;
	private boolean running = false;
	private boolean isServer = false;
	private int clientId = -1;
	private String clientName = "Default";
	private JTextArea statusArea;
	private JTextField ipField;
	private JTextField portField;
	private JTextField nameField;
	private JButton startStopButton;
	private JTextField messageField;
	private JButton sendButton;
	private JRadioButton serverButton;
	private JRadioButton clientButton;
	private Map<Integer, ClientInfo> clients = new HashMap<>();
	private Random random = new Random();
	private Set<Integer> assignedIds = new HashSet<>();

	private static class ClientInfo {
		InetAddress address;
		int port;
		int id;
		String name;

		ClientInfo(InetAddress address, int port, int id, String name) {
			this.address = address;
			this.port = port;
			this.id = id;
			this.name = name;
		}
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new FlatDarkLaf());
				MidiJam frame = new MidiJam();
				frame.setVisible(true);
			} catch (UnsupportedLookAndFeelException e) {
				e.printStackTrace();
			}
		});
	}

	public MidiJam() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(500, 620);
		setTitle("MidiJam");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());
		initComponents();
		setLocationRelativeTo(null);
	}

	private void initComponents() {
		JPanel configurePanel = new JPanel();
		configurePanel.setLayout(new GridLayout(9, 1));
		configurePanel.setBorder(BorderFactory.createTitledBorder("Configure"));

		serverButton = new JRadioButton("Server");
		clientButton = new JRadioButton("Client");
		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(serverButton);
		modeGroup.add(clientButton);
		serverButton.setSelected(true);

		ipField = new JTextField("localhost");
		portField = new JTextField(String.valueOf(DEFAULT_PORT));
		nameField = new JTextField("Default");

		startStopButton = new JButton("Start");
		startStopButton.addActionListener(e -> toggleStartStop());

		configurePanel.add(serverButton);
		configurePanel.add(clientButton);
		configurePanel.add(new JLabel("IP:"));
		configurePanel.add(ipField);
		configurePanel.add(new JLabel("Port:"));
		configurePanel.add(portField);
		configurePanel.add(new JLabel("Name:"));
		configurePanel.add(nameField);
		configurePanel.add(startStopButton);

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));

		// Make the status area auto-scroll
		statusScrollPane.getVerticalScrollBar().setValue(statusScrollPane.getVerticalScrollBar().getMaximum());

		JPanel messagePanel = new JPanel();
		messagePanel.setLayout(new BorderLayout());

		messageField = new JTextField();
		messageField.addActionListener(e -> sendMessage());
		messagePanel.add(messageField, BorderLayout.CENTER);

		sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendMessage());
		messagePanel.add(sendButton, BorderLayout.EAST);

		setLayout(new BorderLayout());
		add(configurePanel, BorderLayout.NORTH);
		add(statusScrollPane, BorderLayout.CENTER);
		add(messagePanel, BorderLayout.SOUTH);
	}

	private void toggleStartStop() {
		if (running) {
			stop();
		} else {
			start();
		}
	}

	private void start() {
		if (running)
			return;
		try {
			port = Integer.parseInt(portField.getText());
			clientName = nameField.getText(); // Get client name

			isServer = serverButton.isSelected(); // Check the mode correctly

			if (isServer) {
				socket = new DatagramSocket(port);
				running = true;
				statusArea.append("Server started on port " + port + "\n");
				new Thread(this::listenForClients).start();
				startStopButton.setText("Stop");
			} else {
				serverAddress = InetAddress.getByName(ipField.getText());
				socket = new DatagramSocket();
				running = true;
				statusArea.append("Client connected to server at " + serverAddress + ":" + port + "\n");
				receiveClientId(); // Receive the client ID from the server
				startStopButton.setText("Stop");
			}
		} catch (Exception e) {
			statusArea.append("Error starting: " + e.getMessage() + "\n");
		}
	}

	private void stop() {
		running = false;
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
		statusArea.append(isServer ? "Server stopped.\n" : "Client disconnected.\n");
		startStopButton.setText("Start");
	}

	private void receiveClientId() {
		new Thread(() -> {
			while (running) {
				try {
					byte[] buffer = new byte[1024];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					String message = new String(packet.getData(), 0, packet.getLength());
					if (message.startsWith("ID ")) {
						clientId = Integer.parseInt(message.substring(3));
						statusArea.append("Received client ID: " + clientId + "\n");
					} else {
						statusArea.append(message + "\n");
					}
					scrollToBottom();
				} catch (IOException e) {
					if (running) {
						statusArea.append("Error receiving ID packet: " + e.getMessage() + "\n");
					}
				}
			}
		}).start();
	}

	private void sendMessage() {
		if (!running)
			return;
		try {
			String message = messageField.getText();
			if (isServer) {
				for (ClientInfo client : clients.values()) {
					String formattedMessage = clientName + ": " + message;
					DatagramPacket packet = new DatagramPacket(formattedMessage.getBytes(), formattedMessage.length(),
							client.address, client.port);
					socket.send(packet);
				}
				statusArea.append("Server broadcasted message: " + message + "\n");
			} else {
				String formattedMessage = clientName + ": " + message;
				DatagramPacket packet = new DatagramPacket(formattedMessage.getBytes(), formattedMessage.length(),
						serverAddress, port);
				socket.send(packet);
				statusArea.append("Me (" + clientName + "): " + message + "\n");
			}
			messageField.setText("");
			scrollToBottom();
		} catch (IOException e) {
			statusArea.append("Error sending message: " + e.getMessage() + "\n");
		}
	}

	private void listenForClients() {
		while (running) {
			try {
				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				String message = new String(packet.getData(), 0, packet.getLength());
				InetAddress clientAddress = packet.getAddress();
				int clientPort = packet.getPort();

				ClientInfo client = clients.values().stream()
						.filter(c -> c.address.equals(clientAddress) && c.port == clientPort).findFirst().orElse(null);

				if (client == null) {
					int newClientId = generateRandomId();
					client = new ClientInfo(clientAddress, clientPort, newClientId, "Client" + newClientId);
					clients.put(newClientId, client);
					statusArea.append(
							"Client connected: ID " + newClientId + " at " + clientAddress + ":" + clientPort + "\n");

					// Send the new client ID to the client
					String idMessage = "ID " + newClientId;
					DatagramPacket idPacket = new DatagramPacket(idMessage.getBytes(), idMessage.length(),
							clientAddress, clientPort);
					socket.send(idPacket);
				}

				String formattedMessage = client.name + ": " + message;
				statusArea.append(formattedMessage + "\n");

				// Relay the message to all clients except the sender
				for (ClientInfo c : clients.values()) {
					if (c.id != client.id) {
						DatagramPacket responsePacket = new DatagramPacket(formattedMessage.getBytes(),
								formattedMessage.length(), c.address, c.port);
						socket.send(responsePacket);
					}
				}
				scrollToBottom();
			} catch (IOException e) {
				if (running) {
					statusArea.append("Error receiving packet: " + e.getMessage() + "\n");
				}
			}
		}
	}

	private int generateRandomId() {
		int randomId;
		do {
			randomId = random.nextInt(MAX_ID + 1);
		} while (assignedIds.contains(randomId));
		assignedIds.add(randomId);
		return randomId;
	}

	private void scrollToBottom() {
		SwingUtilities.invokeLater(() -> {
			JScrollBar verticalScrollBar = ((JScrollPane) statusArea.getParent().getParent()).getVerticalScrollBar();
			verticalScrollBar.setValue(verticalScrollBar.getMaximum());
		});
	}
}
