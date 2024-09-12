package midiJam;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

@SuppressWarnings("serial")
public class StatusIndicatorPanel extends JPanel {

	private boolean connected = false;
	private boolean active = false;
	boolean muted = false;

	private static final Color DISCONNECTED_COLOR = Color.BLACK;
	private static final Color CONNECTED_COLOR = Color.GREEN;
	private static final Color ACTIVITY_BORDER_COLOR = Color.ORANGE;
	private static final Color MUTED_COLOR = Color.RED;

	private static final int TIMEOUT_PERIOD = 250;

	private Timer inactivityTimer;
	private MuteStateListener muteStateListener;

	public StatusIndicatorPanel() {
		setPreferredSize(new Dimension(45, 45));

		inactivityTimer = new Timer(TIMEOUT_PERIOD, e -> {
			setActive(false);
		});
		inactivityTimer.setRepeats(false);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					toggleMuted();
				}
			}
		});
	}

	private void toggleMuted() {
		muted = !muted;
		repaint();
		if (muteStateListener != null) {
			if (muted) {
				muteStateListener.onMuted();
			} else {
				muteStateListener.onUnmuted();
			}
		}
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
		repaint();
	}

	public void setActive(boolean active) {
		this.active = active;
		repaint();

		if (active) {
			inactivityTimer.restart();
		} else {
			inactivityTimer.stop();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D) g;

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int width = getWidth();
		int height = getHeight();

		Color fillColor;
		if (muted) {
			fillColor = MUTED_COLOR;
		} else {
			fillColor = connected ? CONNECTED_COLOR : DISCONNECTED_COLOR;
		}

		Color borderColor = active ? ACTIVITY_BORDER_COLOR : getBackground();

		g2d.setStroke(new BasicStroke(5));
		g2d.setColor(borderColor);
		g2d.drawOval(10, 10, width - 20, height - 20);

		g2d.setColor(fillColor);
		g2d.fillOval(10, 10, width - 20, height - 20);
	}

	public void setMuteStateListener(MuteStateListener listener) {
		this.muteStateListener = listener;
	}

	public interface MuteStateListener {
		void onMuted();

		void onUnmuted();
	}
}
