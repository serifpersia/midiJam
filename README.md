# midiJam

![midiJam Image](https://github.com/user-attachments/assets/3fe4626b-65b7-4d59-a3c6-9cbb13ac3171)

**midiJam** is a Java-based server and client application designed for hosting and participating in online MIDI jamming sessions. Unlike audio-based jamming, midiJam uses MIDI data, which consists of small packets that can be transmitted efficiently, even with varying network latencies. This allows musicians to collaborate in real-time with minimal delay.

## Download
 [![Release](https://img.shields.io/github/release/serifpersia/midiJam.svg?style=flat-square)](https://github.com/serifpersia/midiJam/releases)

## Java Install
Make sure you have Java JRE/JDK installed any LTS 17 or newer. [Temurin](https://adoptium.net/temurin/releases/) is one of the sources you can get it.

## How It Works

### 1. Host Setup

- **Server Application**: Start the server application on your machine. You will be prompted to enter a UDP port number, which will be used for communication between the server and clients.
  
- **Public Access**: You can make your server accessible to the public in one of two ways:
  1. **Port Forwarding**: Forward the chosen UDP port on your router to your server's local IP address.
  2. **Tunnel Service**: Use a tunneling service like [playit.gg](https://playit.gg) to expose your server to the internet without manual port forwarding.

### 2. Client Setup

- **Connect to Server**: Each client connects to the server using the public IP address or the tunnel service URL provided by the host.
  
- **MIDI Channel Selection**: Each client must choose a unique MIDI channel (1-16). Note that macOS users may need to adjust their MIDI channel selection by adding or subtracting 1, due to different indexing conventions between macOS and other operating systems.

### 3. DAW and VSTs

- **Loading VSTs**: Each participant should load their preferred VSTs (Virtual Studio Technology plugins) into a DAW (Digital Audio Workstation) that supports multiple MIDI channels.
  
- **MIDI Routing**:
- For Windows use [loopMIDI](https://www.tobias-erichsen.de/software/loopmidi.html) to create virtual MIDI port that will be used as MIDI Out in the client application. Mac users can follow this [tutorial](https://www.youtube.com/watch?v=IcOA8gHDkgI). And Linux users can do it via terminal
  - DAW or VST that supports layering of patches with different assigned MIDI channel should use virtual port created and used as MIDI Output in midiJam Client app as MIDI Input instead of MIDI device(client app will use this hardware MIDI device as MIDI Input).
  - Assign each VST to the correct MIDI channel, as selected in the midiJam client.
  - For the VST/library/patch you want to control, select the MIDI channel you chose in the midiJam client application.
  - To generate audio from other participants' MIDI data, select their assigned MIDI channel in your DAW or VST with layered patches.
  
  **Note**: MIDI communication is separated by channels, and each person has full control over their channel. This ensures that control messages, like sustain (CC), don’t interfere with others unless the OMNI channel is selected, which merges all channels into one.

![DAW and VST Setup](https://github.com/user-attachments/assets/9be1cee9-abd9-4ff5-b094-192452312b86)

### 4. Chat & Chords

- **Chat**: The client application features a chat window for communication between participants.
  
- **Chords Tab**: Opens a new window displaying real-time MIDI input from clients on an 88-key virtual piano keyboard. Basic chords are recognized, making it easier to share chord progressions or notes, useful for learning or collaborating with others.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
