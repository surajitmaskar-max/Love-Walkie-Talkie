# Love Walkie Talkie - Signaling Server

This is the lightweight, secure WebRTC signaling & pairing backend for the Love Walkie Talkie Android application.

## Features
- **Strict 2-User Room Isolation**: Once two phones are paired, any 3rd party entering the code is blocked with `ROOM_FULL`.
- **Ultra-low latency WebRTC SDP & ICE exchange**.
- **Live Push-to-Talk (PTT) status relay**.
- **Automatic reconnection & session cleanup**.
- **Zero audio storage**: Raw audio streams directly peer-to-peer between the two devices via WebRTC DTLS-SRTP encryption. The server only handles connection handshakes.

## How to Run Locally
```bash
cd server
npm install
npm start
```
By default, the server listens on `http://0.0.0.0:8080`.
- For Android Emulator: `ws://10.0.2.2:8080/ws`
- For physical phones on the same Wi-Fi: `ws://YOUR_COMPUTER_IP:8080/ws`

## Deploying to the Cloud for Free (Worldwide Access)

You can deploy this server in 1 click to any free cloud provider:

### 1. Render (Recommended)
1. Fork or push this repository to GitHub.
2. Go to [render.com](https://render.com) -> New **Web Service**.
3. Select your repository.
4. Set **Root Directory**: `server`
5. Set **Build Command**: `npm install`
6. Set **Start Command**: `node server.js`
7. Click **Create Web Service**.
8. Render will provide a free HTTPS/WSS URL like:
   `wss://your-app-name.onrender.com/ws`
9. Enter this URL in the **Settings** screen inside Love Walkie Talkie on both phones!

### 2. Railway / Fly.io / Google Cloud Run
Use the included `server/Dockerfile` to deploy as a container service.
