package midiJam;

import java.awt.*;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

@SuppressWarnings("serial")
public class MidiJamClient extends JFrame {

	private static int customChannel;
	private static int serverPort;
	private static int clientId;

	private static String clientName;

	private static DatagramSocket clientSocket;
	private static InetAddress serverAddress;

	private MidiDevice inputDevice, outputDevice;
	private MidiReceiver midiReceiver;

	private JComboBox<MidiDevice.Info> inputDeviceDropdown, outputDeviceDropdown;
	private JComboBox<Object> midi_ch_list_dropdown;

	private JToggleButton tglConnect;

	private static JTextPane statusArea;
	private JTextField messageField;
	private JButton sendButton;

	private JLabel lb_inSessionCount;

	public static String chordName;

	public static ChordPanel chordPanelInstance;

	private static final Color[] CHAT_COLORS = { new Color(0, 191, 255), new Color(50, 205, 50), new Color(255, 140, 0),
			new Color(255, 105, 180), new Color(255, 215, 0), new Color(0, 255, 255), new Color(148, 0, 211),
			new Color(0, 255, 127), new Color(255, 69, 0), new Color(255, 20, 147) };

	private static final Map<Integer, Color> clientColorMap = new HashMap<>();
	private static final Random random = new Random();

	public static Color getColorForClientId(int id) {
		if (id == clientId) {
			return Color.RED;
		} else {
			return clientColorMap.computeIfAbsent(id, k -> getRandomColor());
		}
	}

	private static Color getRandomColor() {
		Color color;
		do {
			color = CHAT_COLORS[random.nextInt(CHAT_COLORS.length)];
		} while (color.equals(Color.RED)); // Avoid red
		return color;
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new FlatDarkLaf());
				MidiJamClient frame = new MidiJamClient();
				frame.setVisible(true);
			} catch (UnsupportedLookAndFeelException | MidiUnavailableException e) {
				e.printStackTrace();
			}
		});
	}

	public MidiJamClient() throws MidiUnavailableException {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(350, 450);
		setTitle("MidiJam Client");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());

		initMidi();
		initComponents();

		setLocationRelativeTo(null);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closeClient();
			}
		});
	}

	private void startMidiRouting() {
		if (inputDevice == null || outputDevice == null) {
			MidiDevice.Info inputDeviceInfo = (MidiDevice.Info) inputDeviceDropdown.getSelectedItem();
			MidiDevice.Info outputDeviceInfo = (MidiDevice.Info) outputDeviceDropdown.getSelectedItem();
			;

			try {
				inputDevice = MidiSystem.getMidiDevice(inputDeviceInfo);
				outputDevice = MidiSystem.getMidiDevice(outputDeviceInfo);

				inputDevice.open();
				outputDevice.open();

				List<Receiver> receivers = new ArrayList<>();
				receivers.add(outputDevice.getReceiver());

				midiReceiver.setDelegates(receivers);
				inputDevice.getTransmitter().setReceiver(midiReceiver);

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
		if (outputDevice != null && outputDevice.isOpen()) {
			outputDevice.close();
		}

		inputDevice = null;
		outputDevice = null;

		System.out.println("MIDI Routing stopped.");
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
		outputDeviceDropdown = new JComboBox<>(outputDevices.toArray(new MidiDevice.Info[0]));
		midiReceiver = new MidiReceiver(null);
	}

	private void initComponents() {
		getContentPane().setLayout(new BorderLayout());

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel connectPanel = createConnectPanel();
		tabbedPane.addTab("Connect", null, connectPanel, null);

		JPanel chatPanel = createChatPanel();
		tabbedPane.addTab("Chat", null, chatPanel, null);

		JPanel chordsPanel = new JPanel(new BorderLayout());
		tabbedPane.addTab("Chords", null, chordsPanel, null);

		tabbedPane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int selectedIndex = tabbedPane.getSelectedIndex();
				if (selectedIndex == 2) {
					if (chordPanelInstance == null || !chordPanelInstance.isShowing()) {
						chordPanelInstance = new ChordPanel();
						chordPanelInstance.setVisible(true);
					} else {
						chordPanelInstance.toFront();
						chordPanelInstance.requestFocus();
					}
					tabbedPane.setSelectedIndex(1);
				}
			}
		});
	}

	private JPanel createConnectPanel() {
		JPanel connectPanel = new JPanel(new BorderLayout());

		JPanel connectBtnPanel = createConnectBtnPanel();
		connectPanel.add(connectBtnPanel, BorderLayout.SOUTH);

		JPanel dropdownsPanel = createDropdownsPanel();
		connectPanel.add(dropdownsPanel, BorderLayout.CENTER);

		return connectPanel;
	}

	private JPanel createConnectBtnPanel() {
		JPanel connectBtnPanel = new JPanel(new BorderLayout(0, 0));

		tglConnect = new JToggleButton("Connect");
		tglConnect.addActionListener(e -> {
			if (tglConnect.isSelected()) {
				startClient();
			} else {
				closeClient();
//				chordPanelInstance = null;
			}
		});
		connectBtnPanel.add(tglConnect, BorderLayout.CENTER);

		return connectBtnPanel;
	}

	private JPanel createDropdownsPanel() {
		JPanel dropdownsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		addLabelToPanel("MIDI Input", dropdownsPanel, gbc, 0, 0);
		addDropdownToPanel(inputDeviceDropdown, dropdownsPanel, gbc, 1, 0);

		addLabelToPanel("MIDI Out", dropdownsPanel, gbc, 0, 1);
		addDropdownToPanel(outputDeviceDropdown, dropdownsPanel, gbc, 1, 1);

		addLabelToPanel("MIDI Ch", dropdownsPanel, gbc, 0, 2);
		midi_ch_list_dropdown = createMidiChannelDropdown();
		addDropdownToPanel(midi_ch_list_dropdown, dropdownsPanel, gbc, 1, 2);

		lb_inSessionCount = new JLabel("In Session: ?");
		gbc.gridwidth = 2;
		gbc.insets = new Insets(0, 0, 0, 5);
		gbc.gridx = 0;
		gbc.gridy = 3;
		dropdownsPanel.add(lb_inSessionCount, gbc);

		return dropdownsPanel;
	}

	private void addLabelToPanel(String text, JPanel panel, GridBagConstraints gbc, int x, int y) {
		JLabel label = new JLabel(text);
		gbc.anchor = GridBagConstraints.EAST;
		gbc.insets = new Insets(0, 0, 5, 5);
		gbc.gridx = x;
		gbc.gridy = y;
		panel.add(label, gbc);
	}

	private void addDropdownToPanel(JComponent dropdown, JPanel panel, GridBagConstraints gbc, int x, int y) {
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 5, 0);
		gbc.gridx = x;
		gbc.gridy = y;
		panel.add(dropdown, gbc);
	}

	private JComboBox<Object> createMidiChannelDropdown() {
		JComboBox<Object> dropdown = new JComboBox<>();
		for (int i = 0; i < 16; i++) {
			dropdown.addItem("CH" + String.format("%02d", i + 1));
		}
		dropdown.addActionListener(e -> customChannel = dropdown.getSelectedIndex());
		return dropdown;
	}

	private JPanel createChatPanel() {
		JPanel chatPanel = new JPanel(new BorderLayout(0, 0));

		statusArea = new JTextPane();
		statusArea.setEditable(false);
		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));
		chatPanel.add(statusScrollPane, BorderLayout.CENTER);

		JPanel messagePanel = createMessagePanel();
		chatPanel.add(messagePanel, BorderLayout.SOUTH);

		return chatPanel;
	}

	private JPanel createMessagePanel() {
		JPanel messagePanel = new JPanel(new BorderLayout());

		messageField = new JTextField();
		messageField.addActionListener(e -> sendMessage());
		messagePanel.add(messageField, BorderLayout.CENTER);

		sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendMessage());
		messagePanel.add(sendButton, BorderLayout.EAST);

		return messagePanel;
	}

	private void startClient() {
		try {
			clientSocket = new DatagramSocket();
			ClientSetup clientSetup = promptClientSetup();

			if (clientSetup != null) {
				serverAddress = clientSetup.serverAddress;
				serverPort = clientSetup.serverPort;
				clientName = clientSetup.clientName;

				connectToServer();
			} else {
				tglConnect.setSelected(false);
			}
		} catch (Exception e) {
			showErrorDialog("Failed to connect to server: " + e.getMessage());
			tglConnect.setSelected(false);
		}
	}

	private ClientSetup promptClientSetup() {
		JPanel panel = new JPanel(new GridLayout(0, 1));
		JTextField nameField = new JTextField();
		JTextField ipField = new JTextField("127.0.0.1:25565");

		panel.add(new JLabel("Enter your name:"));
		panel.add(nameField);
		panel.add(new JLabel("Enter server IP:Port:"));
		panel.add(ipField);

		int result = JOptionPane.showConfirmDialog(this, panel, "Client Setup", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (result == JOptionPane.OK_OPTION) {
			try {
				String[] serverDetails = ipField.getText().trim().split(":");
				return new ClientSetup(nameField.getText().trim(), InetAddress.getByName(serverDetails[0]),
						Integer.parseInt(serverDetails[1]));
			} catch (Exception e) {
				showErrorDialog("Invalid IP or Port.");
			}
		}
		return null;
	}

	private void updateSessionLabel(int count) {
		lb_inSessionCount.setText("In Session: " + count);
	}

	private void connectToServer() throws IOException {
		String connectMessage = "CONNECT:" + clientName;
		sendPacket(connectMessage.getBytes());

		byte[] buffer = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		clientSocket.setSoTimeout(2000);

		try {
			clientSocket.receive(packet);
			String idMessage = new String(packet.getData(), 0, packet.getLength());

			if (idMessage.startsWith("ID:")) {
				clientId = Integer.parseInt(idMessage.substring(3));
				appendStatus("Connected to server with ID: " + clientId);
				tglConnect.setText("Disconnect");

				startMidiRouting();
				receiveMessages();
			} else {
				showErrorDialog("Failed to receive ID from server.");
				tglConnect.setSelected(false);
			}
		} catch (SocketTimeoutException ste) {
			showErrorDialog("Connection to server timed out. Please try again.");
			tglConnect.setSelected(false);
		}
	}

	private static class ClientSetup {
		String clientName;
		InetAddress serverAddress;
		int serverPort;

		ClientSetup(String clientName, InetAddress serverAddress, int serverPort) {
			this.clientName = clientName;
			this.serverAddress = serverAddress;
			this.serverPort = serverPort;
		}
	}

	private void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(this, message, "Connection Error", JOptionPane.ERROR_MESSAGE);
	}

	private void sendMessage() {
		String message = messageField.getText().trim();
		if (!message.isEmpty() && clientSocket != null && !clientSocket.isClosed()) {
			try {
				String fullMessage = "TEXT:" + clientId + ":" + clientName + ":" + message;
				sendPacket(fullMessage.getBytes());

				appendColoredStatus(clientName + ": " + message, Color.RED);
				messageField.setText("");
			} catch (Exception e) {
				appendStatus("Failed to send message: " + e.getMessage());
			}
		}
	}

	private byte[] parseMidiData(String message) {
		String[] parts = message.split(":");

		// Ensure that there are enough parts in the message
		if (parts.length < 6) { // Adjust this based on your minimum expected format
			appendStatus("Invalid message format: " + message);
			return new byte[0]; // Return an empty byte array if format is invalid
		}

		// Extract clientName (which is at index 2)
		String remoteClientName = parts[2];

		// Extract chordName (which is the last part of the message)
		String chordName = parts[parts.length - 1];

		// Prepare MIDI data (excluding clientId, clientName, and chordName)
		byte[] midiData = new byte[parts.length - 4]; // Adjust size to exclude clientId, clientName, and chordName

		for (int i = 4; i < parts.length - 1; i++) {
			midiData[i - 4] = Byte.parseByte(parts[i]);
		}

		// Log or update status with extracted data
		// appendStatus("Client Name: " + remoteClientName + ", Chord Name: " +
		// chordName);
		SwingUtilities.invokeLater(() -> chordPanelInstance.updateChordLabel(remoteClientName, chordName));

		return midiData;
	}

	private void sendToMidiDevice(byte[] midiData) {
		try {
			ShortMessage sm = new ShortMessage();
			sm.setMessage(midiData[0] & 0xFF, midiData[1] & 0xFF, midiData[2] & 0xFF);
			midiReceiver.send(sm, -1);

			int command = sm.getCommand();
			int note = sm.getData1();
			int velocity = sm.getData2();
			boolean isNoteOn = command == ShortMessage.NOTE_ON && velocity > 0;
			boolean isNoteOff = command == ShortMessage.NOTE_OFF || velocity == 0;

			System.out.println(note);
			System.out.println(isNoteOn);
			System.out.println(isNoteOff);
//			if (isNoteOn) {
//				SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 1));
//				System.out.println("uhm idk 1");
//			} else if (isNoteOff) {
//
//				SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 0));
//				System.out.println("uhm idk 0");
//			}

		} catch (InvalidMidiDataException e) {
		}
	}

	public static void sendMIDI(MidiMessage message, String chordName) {
		try {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int status = shortMessage.getCommand();
				int channel = shortMessage.getChannel();
				int data1 = shortMessage.getData1();
				int data2 = shortMessage.getData2();

				String midiMessageString = "MIDI:" + clientId + ":" + clientName + ":" + status + ":" + channel + ":"
						+ data1 + ":" + data2 + ":" + chordName;
				sendPacket(midiMessageString.getBytes());
			}
		} catch (Exception e) {
			System.err.println("Failed to send MIDI message: " + e.getMessage());
		}
	}

	private void receiveMessages() {
		new Thread(() -> {
			byte[] buffer = new byte[1024];
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

	private void handleReceivedMessage(String message) {
		if (message.startsWith("TEXT:")) {
			handleTextMessage(message);
		} else if (message.startsWith("MIDI:")) {
			handleMidiMessage(message);

		} else if (message.startsWith("COUNT:")) {
			int count = Integer.parseInt(message.substring(6));
			updateSessionLabel(count);
		} else {
			appendStatus("Unknown message type: " + message);
		}
	}

	private void handleTextMessage(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length == 4) {
			int otherClientId = Integer.parseInt(parts[1]);
			String otherClientName = parts[2];
			String actualMessage = parts[3];
			Color clientColor = getColorForClientId(otherClientId);

			appendColoredStatus(otherClientName + ": " + actualMessage, clientColor);
		} else {
			appendStatus("Invalid TEXT message format.");
		}
	}

	private void appendColoredStatus(String message, Color color) {
		SwingUtilities.invokeLater(() -> {
			StyledDocument doc = statusArea.getStyledDocument();
			Style style = statusArea.addStyle("ClientNameStyle", null);

			StyleConstants.setForeground(style, color);
			StyleConstants.setFontSize(style, 16);
			try {
				int colonIndex = message.indexOf(": ");
				if (colonIndex != -1) {
					doc.insertString(doc.getLength(), message.substring(0, colonIndex + 1), style);
					StyleConstants.setForeground(style, Color.WHITE); // Default color
					doc.insertString(doc.getLength(), message.substring(colonIndex + 1) + "\n", style);
				} else {
					doc.insertString(doc.getLength(), message + "\n", style);
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}

			statusArea.setCaretPosition(doc.getLength());
		});
	}

	private void handleMidiMessage(String message) {
		byte[] midiData = parseMidiData(message);
		sendToMidiDevice(midiData);

	}

	private static void sendPacket(byte[] data) {
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
			clientSocket.send(packet);
		} catch (Exception e) {
			appendStatus("Failed to send packet: " + e.getMessage());
		}
	}

	private void closeClient() {
		stopMidiRouting();

		if (clientSocket != null && !clientSocket.isClosed()) {
			String disconnectMessage = "DISCONNECT:" + clientId;
			sendPacket(disconnectMessage.getBytes());

			clientSocket.close();
			appendStatus("Disconnected from server.");
			tglConnect.setText("Connect");
			lb_inSessionCount.setText("In Session: ?");
		}
	}

	private static void appendStatus(String message) {
		SwingUtilities.invokeLater(() -> {
			try {
				StyledDocument doc = statusArea.getStyledDocument();
				Style style = statusArea.addStyle("DefaultStyle", null);
				StyleConstants.setForeground(style, Color.WHITE);

				doc.insertString(doc.getLength(), message + "\n", style);

				statusArea.setCaretPosition(doc.getLength());
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		});
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

	static class MidiReceiver implements Receiver {

		private MultiOutputReceiver multiOutputReceiver;

		public MidiReceiver(List<Receiver> outputDelegates) {
			multiOutputReceiver = new MultiOutputReceiver(outputDelegates);
		}

		public void setDelegates(List<Receiver> delegates) {
			multiOutputReceiver.setDelegates(delegates);
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage sm = (ShortMessage) message;

				int command = sm.getCommand();
				int status = sm.getCommand();
				int data1 = sm.getData1();
				int data2 = sm.getData2();

				int note = sm.getData1();
				int velocity = sm.getData2();
				boolean isNoteOn = sm.getCommand() == ShortMessage.NOTE_ON && velocity > 0;
				boolean isNoteOff = sm.getCommand() == ShortMessage.NOTE_OFF || velocity == 0;

				if (isNoteOn) {
					ChordFunctions.addNoteToActiveList(note);
					SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 1));

				} else if (isNoteOff) {
					ChordFunctions.removeNoteFromActiveList(note);
					SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 0));
				}
				String chordName = ChordFunctions.setChordName(
						ChordFunctions.getFirstRecognizedChord(new ArrayList<>(ChordFunctions.activeNotes), false));
				ChordFunctions.updateChordName(chordName);
				SwingUtilities.invokeLater(() -> chordPanelInstance.updateChordLabel(clientName, chordName));

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF
						|| command == ShortMessage.CONTROL_CHANGE) {

					try {
						ShortMessage msg = new ShortMessage();
						msg.setMessage(status, customChannel, data1, data2);
						multiOutputReceiver.send(msg, timeStamp);

						sendMIDI(msg, chordName);

					} catch (InvalidMidiDataException e) {
						e.printStackTrace();
					}
				}
			}
		}

		@Override
		public void close() {
			multiOutputReceiver.close();
		}
	}

}
