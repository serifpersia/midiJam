package midiJam;

import java.awt.*;
import java.net.*;
import javax.swing.*;

import com.formdev.flatlaf.FlatDarkLaf;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;

@SuppressWarnings("serial")
public class MidiJamServer extends JFrame {

	static JTextArea statusArea;

	ServerUtils serverUtils;

	public static void main(String[] args) {
		if (args.length > 0 && "--nogui".equals(args[0])) {
			new MidiJamServerCli();
			MidiJamServerCli server = new MidiJamServerCli();
			server.startCliServer(args);
			server.serverUtils.startPingTimer();
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
		setTitle("midiJam Server v1.0.6");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());
		setResizable(false);

		initComponents();
		setLocationRelativeTo(null);

		serverUtils = new ServerUtils(true, statusArea);
		serverUtils.connectedClients = new HashMap<>();

		startGuiServer();
		serverUtils.startPingTimer();

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				serverUtils.closeServer();
			}
		});
	}

	private void initComponents() {
		getContentPane().setLayout(new BorderLayout());

		statusArea = new JTextArea();
		statusArea.setEditable(false);

		JScrollPane statusScrollPane = new JScrollPane(statusArea);
		statusScrollPane.setBorder(BorderFactory.createTitledBorder("Status"));

		getContentPane().add(statusScrollPane, BorderLayout.CENTER);

	}

	private int promptForPort() {
		int port = serverUtils.loadPortFromFile();

		String portStr = JOptionPane.showInputDialog(this, "Enter UDP port (default is 5000):", port);

		if (portStr == null) {
			System.exit(0);
		}

		try {
			port = Integer.parseInt(portStr);
			serverUtils.savePortToFile(port);
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid port number. Please enter a valid integer.", "Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		return port;
	}

	private void startGuiServer() {
		int port = promptForPort();
		try {
			serverUtils.serverSocket = new DatagramSocket(port);
			serverUtils.logger.log("Server running at IP: " + InetAddress.getLocalHost().getHostAddress() + ", Port: "
					+ serverUtils.serverSocket.getLocalPort());
			serverUtils.startServerThread();
		} catch (Exception e) {
			serverUtils.logger.log("Failed to start server: " + e.getMessage());
		}
	}

	static class MidiJamServerCli {

		ServerUtils serverUtils = new ServerUtils(false, null);

		void startCliServer(String[] args) {
			int port = serverUtils.loadPortFromFile();

			for (int i = 0; i < args.length; i++) {
				if ("-port".equals(args[i]) && i + 1 < args.length) {
					try {
						port = Integer.parseInt(args[i + 1]);
						serverUtils.logger.log("Port provided from arguments: " + port);
						serverUtils.savePortToFile(port);
						break;
					} catch (NumberFormatException e) {
						serverUtils.logger.log("Invalid port number in arguments. Using the file/default port.");
					}
				}
			}

			try {
				serverUtils.serverSocket = new DatagramSocket(port);
				serverUtils.logger.log("Server running at IP: " + InetAddress.getLocalHost().getHostAddress()
						+ ", Port: " + serverUtils.serverSocket.getLocalPort());

				serverUtils.startServerThread();
			} catch (Exception e) {
				serverUtils.logger.log("Failed to start server: " + e.getMessage());
			}

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				serverUtils.closeServer();
				serverUtils.logger.log("Server closed gracefully.");
			}));
		}

	}
}
