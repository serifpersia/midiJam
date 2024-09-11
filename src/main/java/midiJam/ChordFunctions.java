package midiJam;

import java.util.*;

public class ChordFunctions {
	public static final Set<Byte> activeNotes = new HashSet<>();
	private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	public static String chordName;

	public static String detectIntervalOrChord(Set<Byte> activeNotes) {
		if (activeNotes.isEmpty()) {
			return "";
		}

		List<Byte> sortedNotes = new ArrayList<>(activeNotes);
		Collections.sort(sortedNotes);

		if (sortedNotes.size() == 2) {
			return detectInterval(sortedNotes);
		} else if (sortedNotes.size() >= 3) {
			return detectChord(sortedNotes);
		} else {
			return detectNotes(sortedNotes);
		}
	}

	private static String activeNotesToString(List<Byte> sortedNotes) {
		StringBuilder notesString = new StringBuilder();
		for (byte midiNote : sortedNotes) {
			int rootMidiNumber = midiNote % 12;
			String noteName = NOTE_NAMES[rootMidiNumber];
			int octave = (midiNote / 12) - 1;
			notesString.append(noteName).append(octave).append(" ");
		}
		return notesString.toString().trim();
	}

	private static String detectNotes(List<Byte> sortedNotes) {
		chordName = "(" + activeNotesToString(sortedNotes) + ")";
		return chordName;
	}

	private static String detectInterval(List<Byte> sortedNotes) {
		byte note1 = sortedNotes.get(0);
		byte note2 = sortedNotes.get(1);
		int interval = Math.abs(note2 - note1);

		int mappedInterval = interval % 24;
		if (mappedInterval > 21) {
			mappedInterval -= 12;
		}

		chordName = getIntervalName(mappedInterval);
		return chordName;
	}

	private static String getIntervalName(int interval) {
		switch (interval) {
		case 0:
			return "Unison";
		case 1:
			return "Minor Second";
		case 2:
			return "Major Second";
		case 3:
			return "Minor Third";
		case 4:
			return "Major Third";
		case 5:
			return "Perfect Fourth";
		case 6:
			return "Tritone";
		case 7:
			return "Perfect Fifth";
		case 8:
			return "Minor Sixth/Aug Fifth";
		case 9:
			return "Major Sixth";
		case 10:
			return "Minor Seventh";
		case 11:
			return "Major Seventh";
		case 12:
			return "Octave";
		case 13:
			return "Flat Ninth";
		case 14:
			return "Ninth";
		case 15:
			return "Minor Tenth/Aug Ninth";
		case 16:
			return "Major Tenth";
		case 17:
			return "Eleventh";
		case 18:
			return "Aug Eleventh";
		case 19:
			return "Perfect Twelfth";
		case 20:
			return "Flat Thirteenth";
		case 21:
			return "Thirteenth";
		default:
			return "Unknown Interval";
		}
	}

	private static String detectChord(List<Byte> sortedNotes) {
		if (sortedNotes.size() < 3) {
			return "Invalid Chord";
		}

		byte root = sortedNotes.get(0);
		Set<Integer> intervals = new HashSet<>();
		for (int i = 1; i < sortedNotes.size(); i++) {
			int interval = (sortedNotes.get(i) - root) % 12;
			if (interval < 0)
				interval += 12;
			intervals.add(interval);
		}

		List<Integer> sortedIntervals = new ArrayList<>(intervals);
		Collections.sort(sortedIntervals);

		String chord = identifyChord(sortedIntervals, sortedNotes);
		return chord;
	}

	private static String identifyChord(List<Integer> intervals, List<Byte> sortedNotes) {
		final Map<Set<Integer>, String> chordMap = new HashMap<>();

		chordMap.put(Set.of(4, 7), "Major");
		chordMap.put(Set.of(3, 7), "Minor");
		chordMap.put(Set.of(3, 6), "Diminished");
		chordMap.put(Set.of(4, 8), "Augmented");
		chordMap.put(Set.of(5, 7), "Sus4");
		chordMap.put(Set.of(2, 7), "Sus2");
		chordMap.put(Set.of(4, 7, 9), "Major 6th");
		chordMap.put(Set.of(3, 7, 9), "Minor 6th");
		chordMap.put(Set.of(4, 7, 10), "Dominant 7th");
		chordMap.put(Set.of(4, 7, 11), "Major 7th");
		chordMap.put(Set.of(3, 7, 10), "Minor 7th");
		chordMap.put(Set.of(3, 6, 9), "Diminished 7th");
		chordMap.put(Set.of(3, 6, 10), "Half-Diminished");
		chordMap.put(Set.of(4, 8, 10), "Augmented 7th");
		chordMap.put(Set.of(2, 4, 7, 10), "Dominant 9th");
		chordMap.put(Set.of(2, 4, 7, 11), "Major 9th");
		chordMap.put(Set.of(2, 3, 7, 10), "Minor 9th");
		chordMap.put(Set.of(2, 3, 6, 9), "Diminished 9th");

		Set<Integer> intervalSet = new HashSet<>(intervals);
		String chordType = chordMap.getOrDefault(intervalSet, "Unknown Chord");

		if (chordType.equals("Unknown Chord")) {
			chordName = "(" + activeNotesToString(sortedNotes) + ")";
			return chordType;
		}

		int rootMidiNumber = sortedNotes.get(0) % 12;
		String rootNote = NOTE_NAMES[rootMidiNumber];

		chordName = rootNote + " " + chordType;
		return chordName;
	}

}
