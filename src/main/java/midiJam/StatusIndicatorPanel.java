package midiJam;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

@SuppressWarnings("serial")
public class StatusIndicatorPanel extends JPanel {

	private boolean connected = false;
	private boolean active = false;
	public boolean muted = false;

	private static final Color DISCONNECTED_COLOR = Color.BLACK;
	private static final Color CONNECTED_COLOR = Color.GREEN;
	private static final Color ACTIVITY_BORDER_COLOR = Color.ORANGE;
	private Color MUTED_COLOR = Color.RED;

	private static final int TIMEOUT_PERIOD = 250;

	private Timer inactivityTimer;

	public StatusIndicatorPanel() {
		setPreferredSize(new Dimension(50, 50));

		inactivityTimer = new Timer(TIMEOUT_PERIOD, e -> {
			setActive(false);
		});
		inactivityTimer.setRepeats(false);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					setMuted(muted);
				}
			}
		});
	}

	public void setMuted(boolean muted) {
		this.muted = !this.muted;
		repaint();
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

}
