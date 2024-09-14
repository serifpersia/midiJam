package midiJam;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

@SuppressWarnings("serial")
public class MidiJamClientGUI extends JFrame {

	private static ClientUtils clientUtils;
	private static MidiJamClientNetworking MidiJamClientNetworking;

	JLabel activeSenderlb;
	MultiStatusIndicatorPanel clientConnectionStatePanel;
	MultiStatusIndicatorPanel clientMidiActivityStatePanel;
	MultiStatusIndicatorPanel clientMuteStatePanel;

	public static ChordPanel chordPanelInstance;
	private JPanel sessionPanelContent;
	JButton tglConnect;
	JComboBox<MidiDevice.Info> inputDeviceDropdown;
	JComboBox<MidiDevice.Info> outputDeviceDropdown;
	JComboBox<Object> midi_ch_list_dropdown;
	JLabel lb_inSessionCount;
	private static int customChannel;
	private JTextPane statusArea;
	private JTextField messageField;
	Map<Integer, JLabel> clientLabels = new HashMap<>();
	Map<Integer, Long> clientPingTimestamps = new HashMap<>();

	private final Map<Integer, Color> clientColorMap = new HashMap<>();
	private final Random random = new Random();

	private final Color[] CHAT_COLORS = { new Color(0, 191, 255), new Color(50, 205, 50), new Color(255, 140, 0),
			new Color(255, 105, 180), new Color(255, 215, 0), new Color(0, 255, 255), new Color(148, 0, 211),
			new Color(0, 255, 127), new Color(255, 69, 0), new Color(255, 20, 147) };

	public Color getColorForClientId(int id) {
		if (id == midiJam.MidiJamClientNetworking.clientId) {
			return Color.RED;
		} else {
			return clientColorMap.computeIfAbsent(id, k -> getRandomColor());
		}
	}

	private Color getRandomColor() {
		Color color;
		do {
			color = CHAT_COLORS[random.nextInt(CHAT_COLORS.length)];
		} while (color.equals(Color.RED));
		return color;
	}

	Set<Integer> mutedClients = new HashSet<>();

	private static boolean isMuted;

	private MidiDevice inputDevice;
	private static MidiDevice outputDevice;

	private MidiReceiver midiReceiver;

	public MidiJamClientGUI(ClientUtils clientUtils) throws MidiUnavailableException {
		MidiJamClientGUI.clientUtils = clientUtils;

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(350, 450);
		setTitle("midiJam Client v1.0.6");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());
		setResizable(false);

		initMidi();

		initComponents();

		setLocationRelativeTo(null);

		loadConfiguration();

		Timer timer = new Timer(true);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				checkForGhostClients();
			}
		}, 0, 10000);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				MidiJamClientNetworking.closeClient();
			}
		});
	}

	private void checkForGhostClients() {
		long currentTime = System.currentTimeMillis();

		Iterator<Map.Entry<Integer, Long>> iterator = clientPingTimestamps.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Long> entry = iterator.next();
			int clientId = entry.getKey();
			long lastPingTime = entry.getValue();

			if (currentTime - lastPingTime > 10000) {
				removeClientRow(String.valueOf(clientId));

				clientLabels.remove(clientId);
				iterator.remove();
			}
		}
	}

	void startMidiRouting() {
		if (inputDevice == null || outputDevice == null) {
			MidiDevice.Info inputDeviceInfo = (MidiDevice.Info) inputDeviceDropdown.getSelectedItem();
			MidiDevice.Info outputDeviceInfo = (MidiDevice.Info) outputDeviceDropdown.getSelectedItem();

			try {
				inputDevice = MidiSystem.getMidiDevice(inputDeviceInfo);
				outputDevice = MidiSystem.getMidiDevice(outputDeviceInfo);

				inputDevice.open();
				outputDevice.open();

				midiReceiver.setReceiver(outputDevice.getReceiver());
				inputDevice.getTransmitter().setReceiver(midiReceiver);

				clientUtils.logger.log("MIDI Routing started.");
			} catch (MidiUnavailableException ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error opening MIDI devices: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	void stopMidiRouting() {
		if (inputDevice != null && inputDevice.isOpen()) {
			inputDevice.close();
		}
		if (outputDevice != null && outputDevice.isOpen()) {
			outputDevice.close();
		}

		inputDevice = null;
		outputDevice = null;

		clientUtils.logger.log("MIDI Routing stopped.");
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

	public void setMidiJamClientNetworking(MidiJamClientNetworking MidiJamClientNetworking) {
		MidiJamClientGUI.MidiJamClientNetworking = MidiJamClientNetworking;
	}

	private void initComponents() {
		getContentPane().setLayout(new BorderLayout());

		JPanel rootTop_Panel = new JPanel();
		getContentPane().add(rootTop_Panel, BorderLayout.NORTH);
		rootTop_Panel.setLayout(new BorderLayout(0, 0));

		activeSenderlb = new JLabel("Active Client: None");
		activeSenderlb.setHorizontalAlignment(SwingConstants.CENTER);
		rootTop_Panel.add(activeSenderlb, BorderLayout.CENTER);

		JPanel rootGridStatusIndicatorsPanel = new JPanel();
		rootTop_Panel.add(rootGridStatusIndicatorsPanel, BorderLayout.EAST);
		rootGridStatusIndicatorsPanel.setLayout(new GridLayout(0, 2, 0, 0));

		clientConnectionStatePanel = new MultiStatusIndicatorPanel(
				MultiStatusIndicatorPanel.PanelType.CLIENT_CONNECTION_STATE);
		clientConnectionStatePanel.setPreferredSize(new Dimension(20, 20));
		rootGridStatusIndicatorsPanel.add(clientConnectionStatePanel);

		clientMidiActivityStatePanel = new MultiStatusIndicatorPanel(
				MultiStatusIndicatorPanel.PanelType.CLIENT_MIDI_ACTIVITY_STATE);
		rootGridStatusIndicatorsPanel.add(clientMidiActivityStatePanel);

		JPanel rootCenter_Panel = new JPanel();
		getContentPane().add(rootCenter_Panel, BorderLayout.CENTER);
		rootCenter_Panel.setLayout(new BorderLayout(0, 0));

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

		JPanel connectPanel = createConnectPanel();
		tabbedPane.addTab("Connect", null, connectPanel, null);

		JScrollPane sessionPanel = createSessionPanel();
		tabbedPane.addTab("Session", null, sessionPanel, null);

		JPanel chatPanel = createChatPanel();
		tabbedPane.addTab("Chat", null, chatPanel, null);

		JPanel chordsPanel = new JPanel(new BorderLayout());
		tabbedPane.addTab("Chords", null, chordsPanel, null);

		tabbedPane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int selectedIndex = tabbedPane.getSelectedIndex();
				if (selectedIndex == 3) {
					if (chordPanelInstance == null || !chordPanelInstance.isShowing()) {
						chordPanelInstance = new ChordPanel();
						chordPanelInstance.setVisible(true);
					} else {
						chordPanelInstance.toFront();
						chordPanelInstance.requestFocus();
					}
					tabbedPane.setSelectedIndex(2);
				}
			}
		});

		rootCenter_Panel.add(tabbedPane, BorderLayout.CENTER);
	}

	private JScrollPane createSessionPanel() {
		sessionPanelContent = new JPanel();
		sessionPanelContent.setLayout(new BoxLayout(sessionPanelContent, BoxLayout.Y_AXIS));

		JScrollPane sessionPanel = new JScrollPane(sessionPanelContent);
		sessionPanel.setVerticalScrollBar(new CustomScrollBar(JScrollBar.VERTICAL));

		return sessionPanel;
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

		tglConnect = new JButton("Connect");
		tglConnect.addActionListener(e -> {
			if (tglConnect.getText().equals("Connect")) {
				MidiJamClientNetworking.connectClient();
			} else {
				MidiJamClientNetworking.disconnectClient();
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
		if (dropdown == null || panel == null) {
			return;
		}
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 5, 0);
		gbc.gridx = x;
		gbc.gridy = y;
		panel.add(dropdown, gbc);
	}

	private JComboBox<Object> createMidiChannelDropdown() {
		JComboBox<Object> dropdown = new JComboBox<>();

		String[] instruments = { "Piano: CH01", "Piano: CH02", "Strings: CH03", "Bass: CH04", "Drums/Percussion: CH05",
				"Lead Synth: CH06", "Pad Synth: CH07", "Guitar: CH08", "Brass: CH09", "Woodwinds: CH10", "Organ: CH11",
				"Arpeggiator Synth: CH12", "Sound Effects/FX: CH13", "Choir/Vocals: CH14", "Ambient/FX: CH15",
				"Lead Vocal Sample: CH16" };

		for (String instrument : instruments) {
			dropdown.addItem(instrument);
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

		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendMessage());
		messagePanel.add(sendButton, BorderLayout.EAST);

		return messagePanel;
	}

	private void sendMessage() {
		String message = messageField.getText().trim();
		if (message.isEmpty()) {
			return;
		}

		if (MidiJamClientNetworking.clientSocket == null || MidiJamClientNetworking.clientSocket.isClosed()) {
			appendStatus("Client socket is not available.");
			return;
		}

		String fullMessage = String.format("TEXT:%s:%s:%s", midiJam.MidiJamClientNetworking.clientId,
				midiJam.MidiJamClientNetworking.clientName, message);
		try {
			MidiJamClientNetworking.sendPacket(fullMessage.getBytes());

			SwingUtilities.invokeLater(() -> {
				appendColoredStatus(String.format("%s: %s", midiJam.MidiJamClientNetworking.clientName, message),
						Color.RED);
				messageField.setText("");
			});
		} catch (Exception e) {
			SwingUtilities.invokeLater(() -> appendStatus("Failed to send message: " + e.getMessage()));
		}
	}

	private class CustomScrollBar extends JScrollBar {
		private static final int SCROLL_INCREMENT = 8;

		public CustomScrollBar(int orientation) {
			super(orientation);
		}

		@Override
		public int getUnitIncrement(int direction) {
			return SCROLL_INCREMENT;
		}

		@Override
		public int getBlockIncrement(int direction) {
			return SCROLL_INCREMENT * 5;
		}
	}

	void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(this, message, "Connection Error", JOptionPane.ERROR_MESSAGE);
	}

	void appendStatus(String message) {
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

	void setDevice(int input, int output, int ch) {
		int inputIndex = (input >= 0 && input < inputDeviceDropdown.getItemCount()) ? input : 0;
		int outputIndex = (output >= 0 && output < outputDeviceDropdown.getItemCount()) ? output : 0;
		int channelIndex = (ch >= 0 && ch < midi_ch_list_dropdown.getItemCount()) ? ch : 0;

		inputDeviceDropdown.setSelectedIndex(inputIndex);
		outputDeviceDropdown.setSelectedIndex(outputIndex);
		midi_ch_list_dropdown.setSelectedIndex(channelIndex);
	}

	MidiJamClientNetworking.ClientSetup promptClientSetup() {
		JPanel panel = new JPanel(new GridLayout(0, 1));

		String[] savedInfo = clientUtils.loadNameAndHostNameFromFile();
		String savedName = savedInfo[0];
		String savedHostName = savedInfo[1];

		JTextField nameField = new JTextField(savedName);
		JTextField ipField = new JTextField(savedHostName);

		panel.add(new JLabel("Enter your name:"));
		panel.add(nameField);
		panel.add(new JLabel("Enter server IP:Port:"));
		panel.add(ipField);

		int result = JOptionPane.showConfirmDialog(this, panel, "Client Setup", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (result == JOptionPane.OK_OPTION) {
			try {
				String[] serverDetails = ipField.getText().trim().split(":");
				if (serverDetails.length != 2) {
					throw new IllegalArgumentException("Invalid server details format");
				}

				String ip = serverDetails[0];
				int port = Integer.parseInt(serverDetails[1]);

				clientUtils.saveNameAndHostNameToFile(nameField.getText().trim(), ipField.getText().trim());

				return new MidiJamClientNetworking.ClientSetup(nameField.getText().trim(), InetAddress.getByName(ip),
						port);
			} catch (Exception e) {
				showErrorDialog("Invalid IP or Port. Please ensure the format is 'IP:Port'.");
			}
		}
		return null;
	}

	void appendColoredStatus(String message, Color color) {
		SwingUtilities.invokeLater(() -> {
			StyledDocument doc = statusArea.getStyledDocument();
			Style style = statusArea.addStyle("ClientNameStyle", null);

			StyleConstants.setForeground(style, color);
			StyleConstants.setFontSize(style, 16);
			try {
				int colonIndex = message.indexOf(": ");
				if (colonIndex != -1) {
					doc.insertString(doc.getLength(), message.substring(0, colonIndex + 1), style);
					StyleConstants.setForeground(style, Color.WHITE);
					doc.insertString(doc.getLength(), message.substring(colonIndex + 1) + "\n", style);
				} else {
					doc.insertString(doc.getLength(), message + "\n", style);
				}
				clientUtils.logger.limitLogSize(doc);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}

			statusArea.setCaretPosition(doc.getLength());
		});
	}

	void updateClientRows(Map<String, String> newClients, Set<String> addedClients, Set<String> removedClients) {
		for (String id : addedClients) {
			String name = newClients.get(id);
			String formattedClientInfo = "Client: ID:" + id + " " + name;
			addClientRow(formattedClientInfo);
		}

		for (String id : removedClients) {
			removeClientRow(id);
		}
	}

	private void addClientRow(String formattedClientInfo) {
		String clientIdStr = formattedClientInfo.split(" ")[1].split(":")[1];
		int clientIdToToggle = Integer.parseInt(clientIdStr);

		JPanel row = new JPanel(new GridBagLayout());
		row.setPreferredSize(new Dimension(0, 25));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 5, 0, 5);

		JPanel leftPanel = new JPanel(new GridBagLayout());

		JLabel clientLabel = new JLabel(formattedClientInfo + " Ping: N/A", SwingConstants.CENTER);
		leftPanel.add(clientLabel);

		clientLabels.put(clientIdToToggle, clientLabel);

		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		row.add(leftPanel, gbc);

		JPanel rightPanel = new JPanel(new GridBagLayout());
		rightPanel.setPreferredSize(new Dimension(50, 50));

		clientMuteStatePanel = new MultiStatusIndicatorPanel(MultiStatusIndicatorPanel.PanelType.CLIENT_MUTE_STATE);
		clientMuteStatePanel.setState(false);
		clientMuteStatePanel.setPreferredSize(new Dimension(35, 35));
		clientMuteStatePanel.setToolTipText("Mute/UnMute Client");

		clientMuteStatePanel.setMuteStateListener(new MultiStatusIndicatorPanel.MuteStateListener() {
			@Override
			public void onMuted() {
				sendMute(clientIdToToggle);

				if (clientIdToToggle == midiJam.MidiJamClientNetworking.clientId) {
					setMuted(true);
				}
			}

			@Override
			public void onUnmuted() {
				sendUnmute(clientIdToToggle);

				if (clientIdToToggle == midiJam.MidiJamClientNetworking.clientId) {
					setMuted(false);
				}

			}

		});

		rightPanel.add(clientMuteStatePanel);

		gbc.weightx = 0.0;
		gbc.gridx = 1;
		gbc.gridy = 0;
		row.add(rightPanel, gbc);

		sessionPanelContent.add(row);
		sessionPanelContent.revalidate();
		sessionPanelContent.repaint();
	}

	private void setMuted(boolean mute) {
		isMuted = mute;
	}

	private void sendMute(int clientIdToMute) {
		String message = String.format("MUTE:%d:%d", midiJam.MidiJamClientNetworking.clientId, clientIdToMute);
		MidiJamClientNetworking.sendPacket(message.getBytes());
	}

	private void sendUnmute(int clientIdToUnmute) {
		String message = String.format("UNMUTE:%d:%d", midiJam.MidiJamClientNetworking.clientId, clientIdToUnmute);
		MidiJamClientNetworking.sendPacket(message.getBytes());
	}

	private void removeClientRow(String clientId) {
		for (Component comp : sessionPanelContent.getComponents()) {
			if (comp instanceof JPanel) {
				JPanel row = (JPanel) comp;
				Component[] rowComponents = row.getComponents();

				if (rowComponents.length > 0 && rowComponents[0] instanceof JPanel) {
					JLabel clientLabel = (JLabel) ((JPanel) rowComponents[0]).getComponent(0);
					if (clientLabel.getText().contains("ID:" + clientId)) {
						sessionPanelContent.remove(row);
						sessionPanelContent.revalidate();
						sessionPanelContent.repaint();
						break;
					}
				}
			}
		}
	}

	void loadConfiguration() {
		File file = new File("clientDetails.config");

		if (file.exists()) {
			try (Scanner scanner = new Scanner(file)) {
				if (scanner.hasNextLine()) {
					int inputDevice = scanner.nextInt();
					int outputDevice = scanner.nextInt();
					int channel = scanner.nextInt();

					setDevice(inputDevice, outputDevice, channel);

					clientUtils.logger.log("Configuration loaded from file.");
				}
			} catch (IOException e) {
				System.err.println("Error reading configuration from file. Using defaults.");
			}
		} else {
			saveConfiguration(0, 0, 0);
		}
	}

	void saveConfiguration(int inputDeviceName, int outputDeviceName, int channelName) {
		try (PrintWriter writer = new PrintWriter("clientDetails.config")) {
			writer.println(inputDeviceName);
			writer.println(outputDeviceName);
			writer.println(channelName);
			clientUtils.logger.log("Configuration saved to file.");
		} catch (IOException e) {
			System.err.println("Failed to save configuration to file: " + e.getMessage());
		}
	}

	void sendToMidiDevice(int status, int channel, int data1, int data2) {
		if (outputDevice == null || !outputDevice.isOpen()) {
			clientUtils.logger.log("MIDI output device not available.");
			return;
		}

		try {
			Receiver receiver = outputDevice.getReceiver();
			receiver.send(new ShortMessage(status, channel, data1, data2), -1);
		} catch (InvalidMidiDataException e) {
			clientUtils.logger.log("Invalid MIDI data exception.");
		} catch (MidiUnavailableException e) {
			clientUtils.logger.log("MIDI device unavailable.");
		}
	}

	static class MidiReceiver implements Receiver {

		private Receiver outputReceiver;

		public MidiReceiver(Receiver outputReceiver) {
			this.outputReceiver = outputReceiver;
		}

		public void setReceiver(Receiver receiver) {
			this.outputReceiver = receiver;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage sm = (ShortMessage) message;
				int command = sm.getCommand();
				int status = command;
				int data1 = sm.getData1();
				int data2 = sm.getData2();

				byte note = (byte) sm.getData1();
				boolean isNoteOn = sm.getCommand() == ShortMessage.NOTE_ON;
				boolean isNoteOff = sm.getCommand() == ShortMessage.NOTE_OFF;

				if (isNoteOn) {
					ChordFunctions.activeNotes.add(note);
					SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 1));
				} else if (isNoteOff) {
					ChordFunctions.activeNotes.remove(note);
					SwingUtilities.invokeLater(() -> chordPanelInstance.piano.setPianoKey(note, 0));
				}

				SwingUtilities.invokeLater(() -> {
					ChordFunctions.detectIntervalOrChord(ChordFunctions.activeNotes);
					chordPanelInstance.updateChordLabel(midiJam.MidiJamClientNetworking.clientName,
							ChordFunctions.chordName);
				});

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF
						|| command == ShortMessage.CONTROL_CHANGE) {

					try {
						ShortMessage msg = new ShortMessage();
						msg.setMessage(status, customChannel, data1, data2);
						outputReceiver.send(msg, -1);

						if (!isMuted) {
							sendMIDI(msg);
							sendChordKeys(note, isNoteOn, ChordFunctions.chordName);
						}

					} catch (InvalidMidiDataException e) {
						e.printStackTrace();
					}
				}
			}
		}

		public static void sendMIDI(MidiMessage message) {
			if (!(message instanceof ShortMessage)) {
				System.err.println("Unsupported MIDI message type.");
				return;
			}

			ShortMessage shortMessage = (ShortMessage) message;
			int status = shortMessage.getCommand();
			int channel = shortMessage.getChannel();
			int data1 = shortMessage.getData1();
			int data2 = shortMessage.getData2();

			String midiMessageString = String.format("MIDI:%s:%s:%d:%d:%d:%d", midiJam.MidiJamClientNetworking.clientId,
					midiJam.MidiJamClientNetworking.clientName, status, channel, data1, data2);

			try {
				MidiJamClientNetworking.sendPacket(midiMessageString.getBytes());
			} catch (Exception e) {
				MidiJamClientNetworking.sendPacket(midiMessageString.getBytes());
			}
		}

		public static void sendChordKeys(int note, boolean isNoteOn, String chordName) {
			try {
				String chordKeysMessage = String.format("CHORD_KEYS:%d:%s:%d:%b:%s",
						midiJam.MidiJamClientNetworking.clientId, midiJam.MidiJamClientNetworking.clientName, note,
						isNoteOn, chordName);
				MidiJamClientNetworking.sendPacket(chordKeysMessage.getBytes());
			} catch (Exception e) {
				System.err.println("Failed to send CHORD_KEYS message: " + e.getMessage());
			}
		}

		@Override
		public void close() {
			if (outputReceiver != null) {
				outputReceiver.close();
			}
		}
	}
}
