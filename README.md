# Love Walkie Talkie (ভালোবাসা ওয়াকি-টকি)

A private, secure, two-person online voice walkie-talkie app built exclusively for couples. Allows only two paired users to communicate through crystal-clear live voice over the internet from anywhere in the world.

---

## Key Features

1. **Private Two-Person Pairing**:
   - Exactly two paired devices per room.
   - Generates cryptographically secure pairing codes (e.g. `LV-8429`).
   - Third-party access is strictly rejected once paired.
   - Full unpair and channel-switching capability.

2. **Live Push-to-Talk (PTT)**:
   - Large central tactile microphone button with glowing pulse animation.
   - Hold to talk, release to listen.
   - Classic walkie-talkie "Roger Beep" chimes and haptic vibrations.
   - Real-time speaking indicators: *"You are speaking"*, *"Partner is speaking"*, *"Connected"*, *"Offline"*.
   - Bilingual status indicators (English & Bengali).

3. **Hands-Free Duplex Mode**:
   - Optional toggle for continuous hands-free two-way conversation.

4. **WebRTC Real-Time Audio**:
   - Direct encrypted peer-to-peer voice streaming (DTLS-SRTP).
   - Echo cancellation, noise suppression, and auto gain control.
   - Public STUN fallback (`stun:stun.l.google.com:19302`) + optional custom TURN.

5. **Background Operation**:
   - Persistent Android Foreground Service (`foregroundServiceType="microphone"`) with active notification.
   - WakeLock to prevent audio interruption when the screen dims or the app is minimized.

6. **Absolute Privacy**:
   - Zero audio recording or raw audio database storage.
   - Pairings stored in a private local SQLite database on your device (Android Room).

---

## Project Structure

```
love-walkie-talkie/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # Permissions (MIC, FOREGROUND, NOTIFICATION, etc.)
│   │   │   ├── java/com/example/
│   │   │   │   ├── LoveWalkieTalkieApp.kt   # Application class (initializes Room & WebRTC)
│   │   │   │   ├── MainActivity.kt          # Compose Navigation host
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/               # Room SQLite persistence (PairingSessionEntity, DAO)
│   │   │   │   │   ├── model/               # Data classes (SignalingPayload, SessionState)
│   │   │   │   │   └── repository/          # WalkieTalkieRepository
│   │   │   │   ├── webrtc/
│   │   │   │   │   ├── WebRtcManager.kt     # PeerConnection, AudioSource, AudioTrack, ICE
│   │   │   │   │   ├── SignalingClient.kt   # OkHttp WebSocket client & auto-reconnect
│   │   │   │   │   └── AudioDeviceManager.kt# Speakerphone/Earpiece routing & Roger Beep
│   │   │   │   ├── service/
│   │   │   │   │   └── WalkieTalkieService.kt # Foreground service & notification
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/          # PttButton, WaveformVisualizer, StatusBanner
│   │   │   │   │   ├── navigation/          # Navigation routes
│   │   │   │   │   ├── screens/             # HomeScreen, PairingScreen, SettingsScreen, PrivacyScreen
│   │   │   │   │   ├── theme/               # Theme.kt, Color.kt, Type.kt
│   │   │   │   │   └── viewmodel/           # WalkieTalkieViewModel
│   │   │   └── res/
│   │   │       ├── drawable/                # Adaptive launcher icons (heart + walkie-talkie)
│   │   │       └── values/                  # Strings, colors, styles
│   │   └── test/                            # Unit & Robolectric tests
├── server/
│   ├── package.json                         # Node.js dependencies (ws)
│   ├── server.js                            # WebSocket pairing & WebRTC signaling server
│   ├── Dockerfile                           # Container deployment specification
│   └── README.md                            # Server deployment guide
├── build.gradle.kts                         # Root gradle
└── metadata.json                            # AI Studio app metadata
```

---

## Backend Setup (Signaling Server)

The app needs a WebSocket signaling server to exchange pairing codes and WebRTC connection metadata (SDP/ICE). Audio itself streams peer-to-peer and is **never** sent through or stored on a database.

### Option 1: Free Cloud Deployment (Recommended for Worldwide Use)
Deploy `server/` to **Render**, **Railway**, or **Glitch**:
1. Push `server/` folder to GitHub.
2. In [Render.com](https://render.com), click **New + -> Web Service**.
3. Select Node.js runtime, build command `npm install`, start command `node server.js`.
4. Render will provide a free secure WebSocket URL:
   `wss://your-app-name.onrender.com/ws`
5. Put this URL in the **Settings** screen in Love Walkie Talkie on both phones.

### Option 2: Local Network (Same Wi-Fi)
```bash
cd server
npm install
npm start
```
Use `ws://YOUR_COMPUTER_LOCAL_IP:8080/ws` (e.g. `ws://192.168.1.100:8080/ws`).

---

## How to Run & Pair on Two Android Phones

1. **Install the APK** on Phone A and Phone B.
2. Ensure both phones have an internet connection (Wi-Fi or Mobile Data).
3. **On Phone A (e.g., You)**:
   - Open the app and grant the Microphone permission.
   - Tap **"Pair with Your Partner"**.
   - Tap **"Create Private Room"**.
   - You will receive a unique secret code (e.g., `LV-8429`).
   - Tap **Share** or **Copy** and send the code to your girlfriend.
4. **On Phone B (e.g., Girlfriend)**:
   - Open the app and grant the Microphone permission.
   - Tap **"Pair with Your Partner"** -> Switch to **"Join Room"** tab.
   - Enter the code `LV-8429` and tap **"Join Private Room"**.
5. **Instant Connection**:
   - Both phones will transition to **"Connected with [Partner]"** with a green heart badge.
   - Press and hold the big pink microphone button to talk.
   - While talking: screen displays *"You are speaking"*, button glows rose.
   - On the other phone: button pulses cyan, screen displays *"[Partner] is speaking"*, and her voice plays out loud in real time!
   - Release the button to finish transmitting; a crisp "Roger Beep" chime signals end of transmission.

---

## How to Generate the Signed Release APK

To create an APK ready for direct installation on phones:

```bash
# Build release APK
gradle :app:assembleRelease
```
The resulting release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

Or generate an Android App Bundle (AAB) for Google Play:
```bash
gradle :app:bundleRelease
```
`app/build/outputs/bundle/release/app-release.aab`

---

## STUN / TURN Server Configuration

- **STUN (Included)**: Free Google public STUN servers are built-in:
  - `stun:stun.l.google.com:19302`
  - `stun:stun1.l.google.com:19302`
  - `stun:stun2.l.google.com:19302`
- **TURN (Optional)**: If both users are on strict enterprise firewalls or symmetric carrier CGNATs, you can add a free TURN server (e.g., [Metered.ca](https://www.metered.ca) or [Xirsys](https://xirsys.com)) in the Settings screen.
