package midiJam;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("serial")
public class MultiStatusIndicatorPanel extends JPanel {

	private PanelType panelType;
	private boolean state = false;

	private BufferedImage connectedImage;
	private BufferedImage disconnectedImage;
	private BufferedImage activeImage;
	private BufferedImage mutedImage;

	private static final int TIMEOUT_PERIOD = 250;
	private Timer inactivityTimer;
	private MuteStateListener muteStateListener;

	public enum PanelType {
		CLIENT_CONNECTION_STATE, CLIENT_MIDI_ACTIVITY_STATE, CLIENT_MUTE_STATE
	}

	public MultiStatusIndicatorPanel(PanelType type) {
		this.panelType = type;
		setOpaque(true);

		try {
			connectedImage = ImageIO.read(getClass().getResource("/connected.png"));
			disconnectedImage = ImageIO.read(getClass().getResource("/disconnected.png"));
			activeImage = ImageIO.read(getClass().getResource("/active.png"));
			mutedImage = ImageIO.read(getClass().getResource("/muted.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		inactivityTimer = new Timer(TIMEOUT_PERIOD, e -> setState(false));
		inactivityTimer.setRepeats(false);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				handleMouseClick(e);
			}
		});
	}

	public void setState(boolean state) {
		this.state = state;
		repaint();

		if (panelType == PanelType.CLIENT_MIDI_ACTIVITY_STATE) {
			if (state) {
				inactivityTimer.restart();
			} else {
				inactivityTimer.stop();
			}
		}
	}

	private void handleMouseClick(MouseEvent e) {
		if (panelType == PanelType.CLIENT_MUTE_STATE && SwingUtilities.isLeftMouseButton(e)) {
			toggleMuted();
		}
	}

	private void toggleMuted() {
		state = !state;
		repaint();
		if (muteStateListener != null) {
			if (state) {
				muteStateListener.onMuted();
			} else {
				muteStateListener.onUnmuted();
			}
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		BufferedImage imageToDraw = getImageToDraw();
		if (imageToDraw != null) {
			g2d.drawImage(imageToDraw, 0, 0, getWidth(), getHeight(), null);
		}
	}

	private BufferedImage getImageToDraw() {
		switch (panelType) {
		case CLIENT_CONNECTION_STATE:
			return state ? connectedImage : disconnectedImage;
		case CLIENT_MIDI_ACTIVITY_STATE:
			return state ? activeImage : disconnectedImage;
		case CLIENT_MUTE_STATE:
			return state ? mutedImage : connectedImage;
		default:
			return null;
		}
	}

	public void setMuteStateListener(MuteStateListener listener) {
		this.muteStateListener = listener;
	}

	public interface MuteStateListener {
		void onMuted();

		void onUnmuted();
	}
}
