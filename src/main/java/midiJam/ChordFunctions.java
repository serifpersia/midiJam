package midiJam;

import java.util.*;

public class ChordFunctions {

	private List<Integer> chord;
	private List<Integer> pattern;
	private static final int MIN_NOTE = 21;
	private static final int MAX_NOTE = 108;
	private static final boolean[] activeNotes = new boolean[MAX_NOTE - MIN_NOTE + 1];

	public static String detectedChord;

	public ChordFunctions(List<Integer> newChord) {
		this.chord = new ArrayList<>(newChord);
		this.pattern = new ArrayList<>();
		makeIntervalPattern();
	}

	public void setChord(List<Integer> newChord) {
		this.chord = new ArrayList<>(newChord);
		makeIntervalPattern();
	}

	public int getSum() {
		return pattern.stream().mapToInt(Integer::intValue).sum();
	}

	public String getStringPattern() {
		return String.join(",", pattern.stream().map(String::valueOf).toArray(String[]::new));
	}

	private void makeIntervalPattern() {
		pattern.clear();
		List<Integer> sortedChord = new ArrayList<>(chord);
		Collections.sort(sortedChord);

		if (sortedChord.size() < 2) {
			pattern.add(0);
			return;
		}

		for (int i = 0; i < sortedChord.size() - 1; i++) {
			int interval = sortedChord.get(i + 1) - sortedChord.get(i);
			if (interval < 0)
				interval += 12;
			pattern.add(interval);
		}
	}

	public static boolean compareChords(ChordFunctions first, ChordFunctions second) {
		int f = first.getSum();
		int s = second.getSum();
		if (f != s)
			return f < s;
		return first.getStringPattern().compareTo(second.getStringPattern()) < 0;
	}

	public static List<Integer> getAsStackedChord(List<Integer> chord, boolean reduce) {
		List<Integer> temp = new ArrayList<>(chord);
		if (reduce) {
			temp = new ArrayList<>(new HashSet<>(temp));
		}
		Collections.sort(temp);

		List<ChordFunctions> possibleChords = new ArrayList<>();
		ChordFunctions baseChord = new ChordFunctions(temp);
		possibleChords.add(baseChord);

		while (nextPermutation(temp)) {
			possibleChords.add(new ChordFunctions(temp));
		}

		possibleChords.sort((chord1, chord2) -> {
			int rootComparison = chord1.getRootPosition().compareTo(chord2.getRootPosition());
			return rootComparison != 0 ? rootComparison : ChordFunctions.compareChords(chord1, chord2) ? -1 : 1;
		});

		return possibleChords.get(0).chord;
	}

	private Integer getRootPosition() {
		return chord.get(0);
	}

	private static boolean nextPermutation(List<Integer> array) {
		int i = array.size() - 2;
		while (i >= 0 && array.get(i) >= array.get(i + 1))
			i--;
		if (i < 0)
			return false;

		int j = array.size() - 1;
		while (array.get(j) <= array.get(i))
			j--;
		Collections.swap(array, i, j);
		Collections.reverse(array.subList(i + 1, array.size()));
		return true;
	}

	public static String getFirstRecognizedChord(List<Integer> chord, boolean flats) {
		if (chord.isEmpty())
			return " ";

		Collections.sort(chord);
		List<Integer> temp = new ArrayList<>(new HashSet<>(chord));
		List<Integer> stackedChord = getAsStackedChord(temp, false);

		String intervalString = ChordName.getIntervalString(stackedChord);

		for (ChordName chordName : ChordName.getChordNames()) {
			if (chordName.equalsIntervalString(intervalString)) {
				return chordName.getName(stackedChord.get(chordName.getRootIndex()), chord.get(0), flats);
			}
		}

		return "(" + listNoteNames(temp, flats) + ")";
	}

	public static String getIntervalName(int semitones) {
		semitones %= 12;
		switch (semitones) {
		case 0:
			return "Unison";
		case 1:
			return "Minor 2nd";
		case 2:
			return "Major 2nd";
		case 3:
			return "Minor 3rd";
		case 4:
			return "Major 3rd";
		case 5:
			return "Perfect 4th";
		case 6:
			return "Tritone";
		case 7:
			return "Perfect 5th";
		case 8:
			return "Minor 6th";
		case 9:
			return "Major 6th";
		case 10:
			return "Minor 7th";
		case 11:
			return "Major 7th";
		default:
			return "";
		}
	}

	public static String listNoteNames(List<Integer> chord, boolean flats) {
		return String.join(", ",
				chord.stream().map(note -> getNoteNameWithoutOctave(note, !flats)).toArray(String[]::new));
	}

	public static String getNoteNameWithoutOctave(int note, boolean useFlats) {
		String[] noteNames = useFlats ? new String[] { "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B" }
				: new String[] { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
		return noteNames[note % 12];
	}

	static class ChordName {

		private final String name;
		private final String pattern;
		private final int rootIndex;

		private static final List<ChordName> chordNames = new ArrayList<>();

		public ChordName(String chordName, String noteString) {
			this.name = chordName;
			this.pattern = getIntervalString(noteString);
			this.rootIndex = 0;
		}

		public static List<ChordName> getChordNames() {
			if (chordNames.isEmpty())
				fillChordDatabase();
			return chordNames;
		}

		public String getName(int rootNote, int bassNote, boolean flats) {
			String chordName = getNoteNameWithoutOctave(name.equals("dim7") || name.equals("+") ? bassNote : rootNote,
					!flats) + name;
			if (bassNote % 12 != rootNote % 12) {
				chordName += "/" + getNoteNameWithoutOctave(bassNote, !flats);
			}
			return chordName;
		}

		public boolean equalsIntervalString(String intervalString) {
			return pattern.equalsIgnoreCase(intervalString);
		}

		public int getRootIndex() {
			return rootIndex;
		}

		public static String getIntervalString(List<Integer> stackedChord) {
			StringBuilder p = new StringBuilder();
			for (int i = 0; i < stackedChord.size() - 1; i++) {
				int interval = (stackedChord.get(i + 1) - stackedChord.get(i) + 12) % 12;
				p.append(interval).append(',');
			}
			if (p.length() > 0)
				p.setLength(p.length() - 1);
			return p.toString();
		}

		public static String getIntervalString(String noteString) {
			List<Integer> notes = Arrays.stream(noteString.split(",")).map(String::trim).map(Integer::parseInt)
					.toList();
			return getIntervalString(notes);
		}

		private static void fillChordDatabase() {
			if (!chordNames.isEmpty())
				return;

			chordNames.add(new ChordName("maj", "0,4,7"));
			chordNames.add(new ChordName("min", "0,3,7"));
			chordNames.add(new ChordName("dim", "0,3,6"));
			chordNames.add(new ChordName("aug", "0,4,8"));
			chordNames.add(new ChordName("7", "0,4,7,10"));
			chordNames.add(new ChordName("maj7", "0,4,7,11"));
			chordNames.add(new ChordName("min7", "0,3,7,10"));
			chordNames.add(new ChordName("m7b5", "0,3,6,10"));
			chordNames.add(new ChordName("dim7", "0,3,6,9"));
			chordNames.add(new ChordName("sus4", "0,5,7"));
			chordNames.add(new ChordName("sus2", "0,2,7"));
			chordNames.add(new ChordName("6", "0,4,7,9"));
			chordNames.add(new ChordName("m6", "0,3,7,9"));
			chordNames.add(new ChordName("+", "0,4,8"));
			chordNames.add(new ChordName("7sus4", "0,5,7,10"));
		}
	}

	static String setChordName(String name) {
		return name;
	}

	public static String getChordName() {
		return detectedChord;
	}

	public static void updateChordName(String name) {
		detectedChord = name;
	}

	private static int noteToIndex(byte note) {
		return note - MIN_NOTE;
	}

	public static void addNoteToActiveList(byte note) {
		if (note >= MIN_NOTE && note <= MAX_NOTE) {
			activeNotes[noteToIndex(note)] = true;
		}
	}

	public static void removeNoteFromActiveList(byte note) {
		if (note >= MIN_NOTE && note <= MAX_NOTE) {
			activeNotes[noteToIndex(note)] = false;
		}
	}

	public static List<Byte> getActiveNotes() {
		List<Byte> notes = new ArrayList<>();
		for (int i = 0; i < activeNotes.length; i++) {
			if (activeNotes[i]) {
				notes.add((byte) (i + MIN_NOTE));
			}
		}
		return notes;
	}

}
