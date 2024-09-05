package midiJam;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;

@SuppressWarnings("serial")
public class ChordPanel extends JFrame {

	DrawPiano piano = new DrawPiano();
	private JLabel lb_midiSrcUser;

	public ChordPanel() {
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(797, 175);
		setTitle("Chords");

		initComponents();
		setLocationRelativeTo(null);
	}

	private void initComponents() {
		lb_midiSrcUser = new JLabel("No MIDI Input");
		lb_midiSrcUser.setFont(new Font("Tahoma", Font.PLAIN, 40));
		lb_midiSrcUser.setHorizontalAlignment(SwingConstants.CENTER);
		getContentPane().add(lb_midiSrcUser, BorderLayout.NORTH);

		getContentPane().add(piano, BorderLayout.CENTER);

	}

	public void updateChordLabel(final String clientName, final String chordName) {
		SwingUtilities.invokeLater(() -> {
			if (chordName == null || chordName.trim().isEmpty()) {
				lb_midiSrcUser.setText("No MIDI Input");
			} else {
				lb_midiSrcUser.setText(clientName + ": " + chordName);
			}
		});
	}

}