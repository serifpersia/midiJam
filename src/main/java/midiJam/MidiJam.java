package midiJam;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;

import java.awt.EventQueue;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

@SuppressWarnings("serial")
public class MidiJam extends JFrame {

	private static final int DEFAULT_PORT = 5000;
	private static final int MAX_ID = 9999;
	private static DatagramSocket socket;
	private static InetAddress serverAddress;
	private static int port = DEFAULT_PORT;
	private boolean running = false;
	private boolean isServer = false;
	private String clientName = "Default";
	private static JTextArea statusArea;
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

		ClientInfo(InetAddress address, int port, int id, String name) {
			this.address = address;
			this.port = port;
		}
	}

	private MidiDevice inputDevice, outputDevice1, outputDevice2;
	private JComboBox<MidiDevice.Info> inputDeviceDropdown, outputDeviceDropdown1, outputDeviceDropdown2;
	private TransposingReceiver transposingReceiver;

	private boolean singleOut = false;
	private JToggleButton singleMIDIOutputOnly;

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new FlatDarkLaf());
				MidiJam frame = new MidiJam();
				frame.setVisible(true);
			} catch (UnsupportedLookAndFeelException | MidiUnavailableException e) {
				e.printStackTrace();
			}
		});
	}

	public MidiJam() throws MidiUnavailableException {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(300, 325);
		setTitle("MidiJam");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());

		initMidi();
		initComponents();
		setLocationRelativeTo(null);
	}

	private void initMidi() throws MidiUnavailableException {
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		List<MidiDevice.Info> inputDevices = new ArrayList<>();
		List<MidiDevice.Info> outputDevices = new ArrayList<>();

		for (MidiDevice.Info info : infos) {
			MidiDevice device = MidiSystem.getMidiDevice(info);
			if (device.getMaxTransmitters() != 0) {
				inputDevices.add(info);
			}
			if (device.getMaxReceivers() != 0) {
				outputDevices.add(info);
			}
		}

		if (inputDevices.isEmpty() || outputDevices.isEmpty()) {
			throw new MidiUnavailableException("No MIDI input or output devices found");
		}

		inputDeviceDropdown = new JComboBox<>(inputDevices.toArray(new MidiDevice.Info[0]));
		outputDeviceDropdown1 = new JComboBox<>(outputDevices.toArray(new MidiDevice.Info[0]));
		outputDeviceDropdown2 = new JComboBox<>(outputDevices.toArray(new MidiDevice.Info[0]));
		transposingReceiver = new TransposingReceiver(null); // delegate will be set later
	}

	private void handleDeviceSelection(JComboBox<MidiDevice.Info> selectedDropdown,
			JComboBox<MidiDevice.Info> otherDropdown) {
		// Avoid redundant action when updating programmatically
		if (selectedDropdown.getSelectedIndex() == otherDropdown.getSelectedIndex()
				&& otherDropdown.getSelectedIndex() != -1) {
			otherDropdown.setSelectedIndex(-1);
		}
	}

	private void startMidiRouting() {
		if (inputDevice == null || outputDevice1 == null || outputDevice2 == null) {
			MidiDevice.Info inputDeviceInfo = (MidiDevice.Info) inputDeviceDropdown.getSelectedItem();
			MidiDevice.Info outputDeviceInfo1 = (MidiDevice.Info) outputDeviceDropdown1.getSelectedItem();
			MidiDevice.Info outputDeviceInfo2 = (MidiDevice.Info) outputDeviceDropdown2.getSelectedItem();

			try {
				inputDevice = MidiSystem.getMidiDevice(inputDeviceInfo);
				outputDevice1 = MidiSystem.getMidiDevice(outputDeviceInfo1);
				outputDevice2 = MidiSystem.getMidiDevice(outputDeviceInfo2);

				inputDevice.open();
				outputDevice1.open();
				outputDevice2.open();

				List<Receiver> receivers = new ArrayList<>();
				receivers.add(outputDevice1.getReceiver());
				if (!singleOut) {
					receivers.add(outputDevice2.getReceiver());
				}

				transposingReceiver.setDelegates(receivers);
				transposingReceiver.setSingleOut(singleOut);
				inputDevice.getTransmitter().setReceiver(transposingReceiver);

				System.out.println("MIDI Routing started.");
			} catch (MidiUnavailableException ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error opening MIDI devices: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void stopMidiRouting() {
		if (inputDevice != null && inputDevice.isOpen()) {
			inputDevice.close();
		}
		if (outputDevice1 != null && outputDevice1.isOpen()) {
			outputDevice1.close();
		}
		if (outputDevice2 != null && outputDevice2.isOpen()) {
			outputDevice2.close();
		}

		inputDevice = null;
		outputDevice1 = null;
		outputDevice2 = null;

		System.out.println("MIDI Routing stopped.");
	}

	private void initComponents() {
		getContentPane().setLayout(new BorderLayout());

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel networkPanel = new JPanel();
		tabbedPane.addTab("Network", null, networkPanel, null);
		networkPanel.setLayout(new BorderLayout(0, 0));

		JPanel modeButton_Panel = new JPanel();
		networkPanel.add(modeButton_Panel, BorderLayout.NORTH);

		serverButton = new JRadioButton("Server");
		clientButton = new JRadioButton("Client");

		serverButton.setHorizontalAlignment(SwingConstants.CENTER);
		clientButton.setHorizontalAlignment(SwingConstants.CENTER);

		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(serverButton);
		modeGroup.add(clientButton);

		serverButton.setSelected(true);

		modeButton_Panel.add(serverButton);
		modeButton_Panel.add(clientButton);

		JPanel Configure_Panel = new JPanel();
		networkPanel.add(Configure_Panel, BorderLayout.CENTER);
		Configure_Panel.setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		Configure_Panel.add(panel, BorderLayout.CENTER);
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[] { 0, 0, 0 };
		gbl_panel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		gbl_panel.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		gbl_panel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panel.setLayout(gbl_panel);

		JLabel lblNewLabel = new JLabel("IP:");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 0;
		panel.add(lblNewLabel, gbc_lblNewLabel);

		ipField = new JTextField("localhost");
		GridBagConstraints gbc_IPField = new GridBagConstraints();
		gbc_IPField.insets = new Insets(0, 0, 5, 0);
		gbc_IPField.fill = GridBagConstraints.HORIZONTAL;
		gbc_IPField.gridx = 1;
		gbc_IPField.gridy = 0;
		panel.add(ipField, gbc_IPField);
		ipField.setColumns(10);

		JLabel lblNewLabel_1 = new JLabel("Port:");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 0;
		gbc_lblNewLabel_1.gridy = 1;
		panel.add(lblNewLabel_1, gbc_lblNewLabel_1);

		portField = new JTextField(String.valueOf(DEFAULT_PORT));
		GridBagConstraints gbc_portField = new GridBagConstraints();
		gbc_portField.insets = new Insets(0, 0, 5, 0);
		gbc_portField.fill = GridBagConstraints.HORIZONTAL;
		gbc_portField.gridx = 1;
		gbc_portField.gridy = 1;
		panel.add(portField, gbc_portField);
		portField.setColumns(10);

		JLabel lblNewLabel_3 = new JLabel("Name:");
		GridBagConstraints gbc_lblNewLabel_3 = new GridBagConstraints();
		gbc_lblNewLabel_3.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_3.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_3.gridx = 0;
		gbc_lblNewLabel_3.gridy = 2;
		panel.add(lblNewLabel_3, gbc_lblNewLabel_3);

		nameField = new JTextField("Default");
		GridBagConstraints gbc_nameField = new GridBagConstraints();
		gbc_nameField.insets = new Insets(0, 0, 5, 0);
		gbc_nameField.fill = GridBagConstraints.HORIZONTAL;
		gbc_nameField.gridx = 1;
		gbc_nameField.gridy = 2;
		panel.add(nameField, gbc_nameField);
		nameField.setColumns(10);

		JLabel lblNewLabel_2 = new JLabel("MIDI Input");
		GridBagConstraints gbc_lblNewLabel_2 = new GridBagConstraints();
		gbc_lblNewLabel_2.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2.gridx = 0;
		gbc_lblNewLabel_2.gridy = 3;
		panel.add(lblNewLabel_2, gbc_lblNewLabel_2);

		GridBagConstraints gbc_comboBox = new GridBagConstraints();
		gbc_comboBox.insets = new Insets(0, 0, 5, 0);
		gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox.gridx = 1;
		gbc_comboBox.gridy = 3;
		panel.add(inputDeviceDropdown, gbc_comboBox);

		JLabel lblNewLabel_2_1 = new JLabel("MIDI Out 1");
		GridBagConstraints gbc_lblNewLabel_2_1 = new GridBagConstraints();
		gbc_lblNewLabel_2_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_2_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2_1.gridx = 0;
		gbc_lblNewLabel_2_1.gridy = 4;
		panel.add(lblNewLabel_2_1, gbc_lblNewLabel_2_1);

		GridBagConstraints gbc_comboBox_1 = new GridBagConstraints();
		gbc_comboBox_1.insets = new Insets(0, 0, 5, 0);
		gbc_comboBox_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox_1.gridx = 1;
		gbc_comboBox_1.gridy = 4;
		panel.add(outputDeviceDropdown1, gbc_comboBox_1);

		JLabel lblNewLabel_2_1_1 = new JLabel("MIDI Out 2");
		GridBagConstraints gbc_lblNewLabel_2_1_1 = new GridBagConstraints();
		gbc_lblNewLabel_2_1_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_2_1_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2_1_1.gridx = 0;
		gbc_lblNewLabel_2_1_1.gridy = 5;
		panel.add(lblNewLabel_2_1_1, gbc_lblNewLabel_2_1_1);

		GridBagConstraints gbc_comboBox_1_1 = new GridBagConstraints();
		gbc_comboBox_1_1.insets = new Insets(0, 0, 5, 0);
		gbc_comboBox_1_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox_1_1.gridx = 1;
		gbc_comboBox_1_1.gridy = 5;
		panel.add(outputDeviceDropdown2, gbc_comboBox_1_1);

		singleMIDIOutputOnly = new JToggleButton("Single MIDI Out");
		GridBagConstraints gbc_btnNewButton_1 = new GridBagConstraints();
		gbc_btnNewButton_1.gridwidth = 2;
		gbc_btnNewButton_1.gridx = 0;
		gbc_btnNewButton_1.gridy = 6;
		panel.add(singleMIDIOutputOnly, gbc_btnNewButton_1);

		startStopButton = new JButton("Start");
		startStopButton.addActionListener(e -> toggleServerClient());
		Configure_Panel.add(startStopButton, BorderLayout.SOUTH);

		JPanel chatPanel = new JPanel();
		tabbedPane.addTab("Chat", null, chatPanel, null);
		chatPanel.setLayout(new BorderLayout(0, 0));

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));

		statusScrollPane.getVerticalScrollBar().setValue(statusScrollPane.getVerticalScrollBar().getMaximum());

		JPanel messagePanel = new JPanel();
		messagePanel.setLayout(new BorderLayout());

		messageField = new JTextField();
		messageField.addActionListener(e -> sendMessage());
		messagePanel.add(messageField, BorderLayout.CENTER);

		sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendMessage());
		messagePanel.add(sendButton, BorderLayout.EAST);

		chatPanel.add(statusScrollPane, BorderLayout.CENTER);
		chatPanel.add(messagePanel, BorderLayout.SOUTH);

		outputDeviceDropdown1
				.addActionListener(e -> handleDeviceSelection(outputDeviceDropdown1, outputDeviceDropdown2));
		outputDeviceDropdown2
				.addActionListener(e -> handleDeviceSelection(outputDeviceDropdown2, outputDeviceDropdown1));

		singleMIDIOutputOnly.addActionListener(e -> {
			singleOut = singleMIDIOutputOnly.isSelected();
			System.out.println(singleOut);
			outputDeviceDropdown2.setEnabled(!singleOut);
		});

	}

	private void toggleServerClient() {
		if (running) {
			stop();
		} else {
			start();
		}
	}

	private void start() {
		try {
			if (serverButton.isSelected()) {
				startServer();
			} else {
				startClient();
			}
		} catch (Exception e) {
			statusArea.append("Error starting: " + e.getMessage() + "\n");
		}
	}

	private void stop() {
		running = false;
		stopMidiRouting();
		startStopButton.setText("Start");
		socket.close();
		statusArea.append("Server/Client stopped.\n");
	}

	private void startServer() throws IOException, MidiUnavailableException {
		isServer = true;
		running = true;
		port = Integer.parseInt(portField.getText().trim());

		socket = new DatagramSocket(port);
		statusArea.append("Server started on port " + port + "\n");

		// Start MIDI setup
		// setUpMidiOutput();

		new Thread(this::listenForClients).start();
		startStopButton.setText("Stop");
	}

	private void startClient() throws IOException, MidiUnavailableException {
		isServer = false;
		running = true;
		clientName = nameField.getText().trim();
		serverAddress = InetAddress.getByName(ipField.getText().trim());
		port = Integer.parseInt(portField.getText().trim());

		socket = new DatagramSocket();
		statusArea.append("Client started. Connecting to " + serverAddress + ":" + port + "\n");

		// Request client ID from server
		sendClientInfo();

		// Start MIDI setup
		startMidiRouting();

		new Thread(this::listenForServer).start();
		startStopButton.setText("Stop");
	}

	private void sendClientInfo() throws IOException {
		String infoMessage = "INFO:" + clientName;
		DatagramPacket packet = new DatagramPacket(infoMessage.getBytes(), infoMessage.length(), serverAddress, port);
		socket.send(packet);
	}

	private void listenForClients() {
		while (running) {
			try {
				byte[] buffer = new byte[128];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				String message = new String(packet.getData(), 0, packet.getLength());
				statusArea.append("Received message: " + message + "\n"); // Debug line
				InetAddress clientAddress = packet.getAddress();
				int clientPort = packet.getPort();

				String[] parts = message.split(":", 3);
				if (parts.length >= 2) {
					String type = parts[0];
					String clientName = parts[1];
					String content = parts.length > 2 ? parts[2] : "";

					if (type.equals("TEXT")) {
						statusArea.append(clientName + ": " + content + "\n");
						// Scroll to the bottom of the status area
						scrollToBottom();
						// Relay text messages to all clients except the sender
						for (ClientInfo client : clients.values()) {
							if (client.port != clientPort) {
								DatagramPacket responsePacket = new DatagramPacket(message.getBytes(), message.length(),
										client.address, client.port);
								socket.send(responsePacket);
							}
						}
					} else if (type.equals("INFO")) {
						// Register a new client
						int id = assignClientId();
						clients.put(id, new ClientInfo(clientAddress, clientPort, id, clientName));
						statusArea.append("New client connected: " + clientName + " (ID: " + id + ")\n");
						scrollToBottom();
					} else if (type.equals("MIDI")) {
						statusArea.append(clientName + ":" + content + "\n");
						scrollToBottom();
						// Relay MIDI messages to all clients except the sender
						for (ClientInfo client : clients.values()) {
							if (client.port != clientPort) {
								DatagramPacket responsePacket = new DatagramPacket(message.getBytes(), message.length(),
										client.address, client.port);
								socket.send(responsePacket);
							}
						}
					}
				}
			} catch (IOException e) {
				if (running) {
					statusArea.append("Error receiving packet: " + e.getMessage() + "\n");
					scrollToBottom();
				}
			}
		}
	}

	private void listenForServer() {
		while (running) {
			try {
				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				String message = new String(packet.getData(), 0, packet.getLength());

				String[] parts = message.split(":", 3);
				if (parts.length >= 2) {
					String type = parts[0];
					String clientName = parts[1];
					String content = parts.length > 2 ? parts[2] : "";

					if (type.equals("TEXT")) {
						statusArea.append(clientName + ": " + content + "\n");
					} else if (type.equals("MIDI")) {
						// Handle MIDI messages from server
						handleMIDIMessage(clientName + ":" + content);
						scrollToBottom();
					}
				}
			} catch (IOException e) {
				if (running) {
					statusArea.append("Error receiving packet: " + e.getMessage() + "\n");
				}
			}
		}
	}

	private void handleMIDIMessage(String content) {
		try {
			ShortMessage shortMessage = convertStringToShortMessage(content);

			if (outputDevice1 != null && outputDevice1.isOpen()) {
				try {
					outputDevice1.getReceiver().send(shortMessage, -1);
				} catch (MidiUnavailableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (outputDevice2 != null && outputDevice2.isOpen() && !singleOut) {
				try {
					outputDevice2.getReceiver().send(shortMessage, -1);
				} catch (MidiUnavailableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (InvalidMidiDataException e) {
			statusArea.append("Invalid MIDI data: " + content + "\n");
		}
	}

	private ShortMessage convertStringToShortMessage(String content) throws InvalidMidiDataException {
		String[] parts = content.split(":");
		if (parts.length == 4) {
			int command = Integer.parseInt(parts[0]);
			int channel = Integer.parseInt(parts[1]);
			int data1 = Integer.parseInt(parts[2]);
			int data2 = Integer.parseInt(parts[3]);

			ShortMessage shortMessage = new ShortMessage();
			shortMessage.setMessage(command, channel, data1, data2);
			return shortMessage;
		} else {
			throw new InvalidMidiDataException("Invalid MIDI message format.");
		}
	}

	private void sendMessage() {
		String message = messageField.getText().trim();
		if (!message.isEmpty()) {
			try {
				String messageToSend = "TEXT:" + clientName + ":" + message;
				byte[] messageBytes = messageToSend.getBytes();

				DatagramPacket packet;
				if (isServer) {
					// Send text messages to all clients
					for (ClientInfo client : clients.values()) {
						packet = new DatagramPacket(messageBytes, messageBytes.length, client.address, client.port);
						socket.send(packet);
					}
				} else {
					packet = new DatagramPacket(messageBytes, messageBytes.length, serverAddress, port);
					socket.send(packet);
				}

				statusArea.append("You: " + message + "\n");
				messageField.setText("");
			} catch (IOException e) {
				statusArea.append("Error sending message: " + e.getMessage() + "\n");
			}
		}
	}

	private int assignClientId() {
		int id;
		do {
			id = random.nextInt(MAX_ID);
		} while (assignedIds.contains(id));
		assignedIds.add(id);
		return id;
	}

	private void scrollToBottom() {
		SwingUtilities.invokeLater(() -> {
			JScrollBar verticalScrollBar = ((JScrollPane) statusArea.getParent().getParent()).getVerticalScrollBar();
			verticalScrollBar.setValue(verticalScrollBar.getMaximum());
		});
	}

	static class TransposingReceiver implements Receiver {
		private int transposeValue = 0;
		private int octaveShift = 0;
		private MultiOutputReceiver multiOutputReceiver;
		private boolean singleOut = false;

		private ShortMessage transposedMessage = new ShortMessage();

		public TransposingReceiver(List<Receiver> outputDelegates) {
			multiOutputReceiver = new MultiOutputReceiver(outputDelegates);
		}

		public void setTransposeValue(int transposeValue) {
			this.transposeValue = Math.max(-12, Math.min(12, transposeValue));
		}

		public int getTransposeValue() {
			return transposeValue;
		}

		public void setOctaveShift(int octaveShift) {
			this.octaveShift = Math.max(-7, Math.min(7, octaveShift));
		}

		public int getOctaveShift() {
			return octaveShift;
		}

		public void setDelegates(List<Receiver> delegates) {
			multiOutputReceiver.setDelegates(delegates);
		}

		public void setSingleOut(boolean singleOut) {
			this.singleOut = singleOut;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int command = shortMessage.getCommand();

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
					int originalData = shortMessage.getData1();
					int transposedData = (originalData + transposeValue + (octaveShift * 12)) % 128;

					try {
						transposedMessage.setMessage(command, shortMessage.getChannel(), transposedData,
								shortMessage.getData2());

						if (singleOut) {
							if (multiOutputReceiver.getDelegates().size() > 0) {
								multiOutputReceiver.getDelegates().get(0).send(transposedMessage, timeStamp);
							}
						} else {
							multiOutputReceiver.send(transposedMessage, timeStamp);
						}

					} catch (InvalidMidiDataException e) {
						e.printStackTrace();
					}

				} else if (command == ShortMessage.CONTROL_CHANGE) {
					// Forward Control Change (CC) messages unchanged
					multiOutputReceiver.send(shortMessage, timeStamp);
				}
			}

			sendMidiToServer(transposedMessage);
			System.out.println("Command: " + transposedMessage.getCommand());
			System.out.println("Channel: " + transposedMessage.getChannel());
			System.out.println("Data1: " + transposedMessage.getData1());
			System.out.println("Data2: " + transposedMessage.getData2());

		}

		@Override
		public void close() {
			multiOutputReceiver.close();
		}

	}

	private static void sendMidiToServer(MidiMessage message) {
		if (serverAddress != null && socket != null) {
			try {
				String midiMessage = "MIDI:" + convertMidiMessageToString(message);
				DatagramPacket packet = new DatagramPacket(midiMessage.getBytes(), midiMessage.length(), serverAddress,
						port);
				socket.send(packet);
			} catch (IOException e) {
				statusArea.append("Error sending MIDI message to server: " + e.getMessage() + "\n");
			}
		}
	}

	private static String convertMidiMessageToString(MidiMessage message) {
		if (message instanceof ShortMessage) {
			ShortMessage shortMessage = (ShortMessage) message;
			return String.format("%d:%d:%d:%d", shortMessage.getCommand(), shortMessage.getChannel(),
					shortMessage.getData1(), shortMessage.getData2());
		}
		return "";
	}

	static class MultiOutputReceiver implements Receiver {
		private List<Receiver> delegates = new ArrayList<>(10);

		public MultiOutputReceiver(List<Receiver> delegates) {
			if (delegates != null) {
				this.delegates.addAll(delegates);
			}
		}

		public void setDelegates(List<Receiver> delegates) {
			this.delegates.clear();
			if (delegates != null) {
				this.delegates.addAll(delegates);
			}
		}

		public List<Receiver> getDelegates() {
			return delegates;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			for (Receiver delegate : delegates) {
				delegate.send(message, timeStamp);
			}
		}

		@Override
		public void close() {
			for (Receiver delegate : delegates) {
				delegate.close();
			}
			delegates.clear();
		}
	}
}
