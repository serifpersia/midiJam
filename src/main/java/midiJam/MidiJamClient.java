package midiJam;

import java.awt.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;

import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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

	private static JTextArea statusArea;
	private JTextField messageField;
	private JButton sendButton;

	private JLabel lb_inSessionCount;

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
		setSize(300, 325);
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

		new ButtonGroup();

		JPanel connectPanel = new JPanel();
		tabbedPane.addTab("Connect", null, connectPanel, null);
		connectPanel.setLayout(new BorderLayout());

		JPanel connectbtn_Panel = new JPanel();
		connectPanel.add(connectbtn_Panel, BorderLayout.SOUTH);
		connectbtn_Panel.setLayout(new BorderLayout(0, 0));

		tglConnect = new JToggleButton("Connect");

		tglConnect.addActionListener(e -> {
			if (tglConnect.isSelected()) {
				startClient();
			} else {
				closeClient();
			}
		});
		connectbtn_Panel.add(tglConnect);

		JPanel dropdowns_Panel = new JPanel();
		connectPanel.add(dropdowns_Panel, BorderLayout.CENTER);
		GridBagLayout gbl_dropdowns_Panel = new GridBagLayout();
		gbl_dropdowns_Panel.columnWidths = new int[] { 0, 0, 0 };
		gbl_dropdowns_Panel.rowHeights = new int[] { 0, 0, 0, 0, 0 };
		gbl_dropdowns_Panel.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		gbl_dropdowns_Panel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		dropdowns_Panel.setLayout(gbl_dropdowns_Panel);

		JLabel lbMidiInput = new JLabel("MIDI Input");
		GridBagConstraints gbc_lbMidiInput = new GridBagConstraints();
		gbc_lbMidiInput.insets = new Insets(0, 0, 5, 5);
		gbc_lbMidiInput.anchor = GridBagConstraints.EAST;
		gbc_lbMidiInput.gridx = 0;
		gbc_lbMidiInput.gridy = 0;
		dropdowns_Panel.add(lbMidiInput, gbc_lbMidiInput);

		GridBagConstraints gbc_inputDeviceDropdown = new GridBagConstraints();
		gbc_inputDeviceDropdown.insets = new Insets(0, 0, 5, 0);
		gbc_inputDeviceDropdown.fill = GridBagConstraints.HORIZONTAL;
		gbc_inputDeviceDropdown.gridx = 1;
		gbc_inputDeviceDropdown.gridy = 0;
		dropdowns_Panel.add(inputDeviceDropdown, gbc_inputDeviceDropdown);

		JLabel lblMidiOut = new JLabel("MIDI Out");
		GridBagConstraints gbc_lblMidiOut = new GridBagConstraints();
		gbc_lblMidiOut.anchor = GridBagConstraints.EAST;
		gbc_lblMidiOut.insets = new Insets(0, 0, 5, 5);
		gbc_lblMidiOut.gridx = 0;
		gbc_lblMidiOut.gridy = 1;
		dropdowns_Panel.add(lblMidiOut, gbc_lblMidiOut);

		GridBagConstraints gbc_outputDeviceDropdown = new GridBagConstraints();
		gbc_outputDeviceDropdown.insets = new Insets(0, 0, 5, 0);
		gbc_outputDeviceDropdown.fill = GridBagConstraints.HORIZONTAL;
		gbc_outputDeviceDropdown.gridx = 1;
		gbc_outputDeviceDropdown.gridy = 1;
		dropdowns_Panel.add(outputDeviceDropdown, gbc_outputDeviceDropdown);

		JLabel lblMidiCh = new JLabel("MIDI Ch");
		GridBagConstraints gbc_lblMidiCh = new GridBagConstraints();
		gbc_lblMidiCh.anchor = GridBagConstraints.EAST;
		gbc_lblMidiCh.insets = new Insets(0, 0, 5, 5);
		gbc_lblMidiCh.gridx = 0;
		gbc_lblMidiCh.gridy = 2;
		dropdowns_Panel.add(lblMidiCh, gbc_lblMidiCh);

		midi_ch_list_dropdown = new JComboBox<Object>();

		for (int i = 0; i <= 15; i++) {
			midi_ch_list_dropdown.addItem("CH" + String.format("%02d", i + 1));
		}

		GridBagConstraints gbc_midi_ch_list_dropdown = new GridBagConstraints();
		gbc_midi_ch_list_dropdown.insets = new Insets(0, 0, 5, 0);
		gbc_midi_ch_list_dropdown.fill = GridBagConstraints.HORIZONTAL;
		gbc_midi_ch_list_dropdown.gridx = 1;
		gbc_midi_ch_list_dropdown.gridy = 2;

		midi_ch_list_dropdown.addActionListener(e -> {
			int selectedIndex = midi_ch_list_dropdown.getSelectedIndex();
			customChannel = selectedIndex;
		});

		dropdowns_Panel.add(midi_ch_list_dropdown, gbc_midi_ch_list_dropdown);

		lb_inSessionCount = new JLabel("In Session: 0/100");
		GridBagConstraints gbc_lb_inSessionCount = new GridBagConstraints();
		gbc_lb_inSessionCount.gridwidth = 2;
		gbc_lb_inSessionCount.insets = new Insets(0, 0, 0, 5);
		gbc_lb_inSessionCount.gridx = 0;
		gbc_lb_inSessionCount.gridy = 3;
		dropdowns_Panel.add(lb_inSessionCount, gbc_lb_inSessionCount);

		JPanel chatPanel = new JPanel();
		tabbedPane.addTab("Chat", null, chatPanel, null);
		chatPanel.setLayout(new BorderLayout(0, 0));

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));

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
	}

	private void startClient() {
		try {
			clientSocket = new DatagramSocket();

			JPanel panel = new JPanel(new GridLayout(0, 1));
			JTextField nameField = new JTextField();
			JTextField ipField = new JTextField("127.0.0.1:5000");
			panel.add(new JLabel("Enter your name:"));
			panel.add(nameField);
			panel.add(new JLabel("Enter server IP:Port:"));
			panel.add(ipField);

			int result = JOptionPane.showConfirmDialog(this, panel, "Client Setup", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE);
			if (result == JOptionPane.OK_OPTION) {
				clientName = nameField.getText().trim();
				String[] serverDetails = ipField.getText().trim().split(":");
				serverAddress = InetAddress.getByName(serverDetails[0]);
				serverPort = Integer.parseInt(serverDetails[1]);

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
			} else {
				tglConnect.setSelected(false);
			}
		} catch (Exception e) {
			showErrorDialog("Failed to connect to server: " + e.getMessage());
			tglConnect.setSelected(false);
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
				appendStatus(clientName + ": " + message);
				messageField.setText("");
			} catch (Exception e) {
				appendStatus("Failed to send message: " + e.getMessage());
			}
		}
	}

	public static void sendMIDI(MidiMessage message) {
		try {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int status = shortMessage.getCommand();
				int channel = shortMessage.getChannel();
				int data1 = shortMessage.getData1();
				int data2 = shortMessage.getData2();

				String midiMessageString = "MIDI:" + clientId + ":" + clientName + ":" + status + ":" + channel + ":"
						+ data1 + ":" + data2;
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

					if (message.startsWith("TEXT:")) {
						String[] parts = message.split(":", 4);
						if (parts.length == 4) {
							String otherClientName = parts[2];
							String actualMessage = parts[3];
							String formattedMessage = otherClientName + ": " + actualMessage;
							SwingUtilities.invokeLater(() -> appendStatus(formattedMessage));
						} else {
							SwingUtilities.invokeLater(() -> appendStatus("Invalid TEXT message format."));
						}
					} else if (message.startsWith("MIDI:")) {
						String[] parts = message.split(":");
						if (parts.length == 7) {
							// String senderName = parts[2];
							int status = Integer.parseInt(parts[3]);
							int channel = Integer.parseInt(parts[4]);
							int data1 = Integer.parseInt(parts[5]);
							int data2 = Integer.parseInt(parts[6]);

							// String formattedMessage = "MIDI from " + senderName + ": " + ", Status=" +
							// status
							// + ", Channel=" + channel + ", Data1=" + data1 + ", Data2=" + data2;
							// SwingUtilities.invokeLater(() -> appendStatus(formattedMessage));
							routeNetworkMIDI(status, channel, data1, data2);
						} else {
							SwingUtilities.invokeLater(() -> appendStatus("Invalid MIDI message format."));
						}
					} else {
						SwingUtilities.invokeLater(() -> appendStatus("Unknown message type: "));
					}
				} catch (SocketTimeoutException e) {
				} catch (Exception e) {
					if (!clientSocket.isClosed()) {
						SwingUtilities.invokeLater(() -> appendStatus("Failed to receive message: " + e.getMessage()));
					}
				}
			}
		}).start();
	}

	private void routeNetworkMIDI(int status, int channel, int data1, int data2) {
		try {
			ShortMessage shortMessage = new ShortMessage();
			shortMessage.setMessage(status, channel, data1, data2);

			if (outputDevice != null && outputDevice.isOpen()) {
				Receiver receiver = outputDevice.getReceiver();
				receiver.send(shortMessage, -1);
			}
		} catch (InvalidMidiDataException e) {

		} catch (MidiUnavailableException e) {

		}
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
		}
	}

	private static void appendStatus(String message) {
		SwingUtilities.invokeLater(() -> statusArea.append(message + "\n"));
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
				ShortMessage shortMessage = (ShortMessage) message;
				int command = shortMessage.getCommand();
				int status = shortMessage.getCommand();
				int data1 = shortMessage.getData1();
				int data2 = shortMessage.getData2();

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF
						|| command == ShortMessage.CONTROL_CHANGE) {

					try {
						ShortMessage msg = new ShortMessage();
						msg.setMessage(status, customChannel, data1, data2);
						multiOutputReceiver.send(msg, timeStamp);

						sendMIDI(msg);

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
