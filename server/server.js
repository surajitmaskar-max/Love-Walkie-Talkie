/**
 * Love Walkie Talkie - Signaling & Pairing Server
 * 
 * Simple, fast, secure pairwise WebRTC signaling server for Android Love Walkie Talkie.
 * Enforces strict 2-user limit per room.
 */

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = process.env.PORT || 8080;

// Rooms Map: roomCode -> { host: { ws, senderId, name }, guest: { ws, senderId, name }, createdAt }
const rooms = new Map();

// Map from WebSocket to { roomCode, role, senderId }
const clientMeta = new WeakMap();

const server = http.createServer((req, res) => {
  if (req.url === '/health' || req.url === '/') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'healthy',
      app: 'Love Walkie Talkie Signaling Server',
      activeRooms: rooms.size,
      timestamp: new Date().toISOString()
    }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

const wss = new WebSocketServer({ server });

function generateSecureCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const prefix = chars[Math.floor(Math.random() * chars.length)] + chars[Math.floor(Math.random() * chars.length)];
  const num = Math.floor(1000 + Math.random() * 9000);
  return `${prefix}-${num}`;
}

function sendJson(ws, payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function sendError(ws, errorCode, errorMessage) {
  sendJson(ws, {
    type: 'error',
    errorCode,
    errorMessage,
    timestamp: Date.now()
  });
}

wss.on('connection', (ws, req) => {
  const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  console.log(`[Connect] New client connected from ${ip}`);

  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (message) => {
    try {
      const payload = JSON.parse(message.toString());
      const type = payload.type;

      switch (type) {
        case 'ping': {
          sendJson(ws, { type: 'pong', timestamp: Date.now() });
          break;
        }

        case 'create_room': {
          let code = payload.roomCode ? payload.roomCode.trim().toUpperCase() : generateSecureCode();
          while (rooms.has(code)) {
            code = generateSecureCode();
          }

          const room = {
            code,
            host: {
              ws,
              senderId: payload.senderId || 'host',
              name: payload.displayName || 'Me'
            },
            guest: null,
            createdAt: Date.now()
          };

          rooms.set(code, room);
          clientMeta.set(ws, { roomCode: code, role: 'host', senderId: payload.senderId });

          console.log(`[Room Created] Code: ${code} by user: ${payload.senderId}`);
          sendJson(ws, {
            type: 'room_created',
            roomCode: code,
            timestamp: Date.now()
          });
          break;
        }

        case 'join_room': {
          const code = payload.roomCode ? payload.roomCode.trim().toUpperCase() : '';
          const room = rooms.get(code);

          if (!room) {
            console.log(`[Join Failed] Room not found: ${code}`);
            sendError(ws, 'ROOM_NOT_FOUND', `Room ${code} was not found. Please verify the code.`);
            return;
          }

          // Check if room is already full (strictly 2 users max)
          if (room.guest && room.guest.ws !== ws && room.host && room.host.ws !== ws) {
            console.log(`[Join Denied] Room ${code} is full (already has 2 users). Rejecting 3rd party.`);
            sendError(ws, 'ROOM_FULL', 'This private room is already full. Only two paired users are permitted.');
            return;
          }

          // If reconnecting as host
          if (room.host && room.host.senderId === payload.senderId) {
            room.host.ws = ws;
            clientMeta.set(ws, { roomCode: code, role: 'host', senderId: payload.senderId });
            sendJson(ws, { type: 'room_joined', roomCode: code, timestamp: Date.now() });
            if (room.guest && room.guest.ws.readyState === WebSocket.OPEN) {
              sendJson(room.guest.ws, { type: 'partner_joined', displayName: payload.displayName });
              sendJson(ws, { type: 'partner_joined', displayName: room.guest.name });
            }
            return;
          }

          // Register as guest
          room.guest = {
            ws,
            senderId: payload.senderId || 'guest',
            name: payload.displayName || 'Partner'
          };
          clientMeta.set(ws, { roomCode: code, role: 'guest', senderId: payload.senderId });

          console.log(`[Room Paired] Guest joined room ${code}`);
          sendJson(ws, {
            type: 'room_joined',
            roomCode: code,
            timestamp: Date.now()
          });

          // Notify host that partner has joined
          if (room.host && room.host.ws.readyState === WebSocket.OPEN) {
            sendJson(room.host.ws, {
              type: 'partner_joined',
              displayName: payload.displayName,
              timestamp: Date.now()
            });
            // Also notify guest of host presence
            sendJson(ws, {
              type: 'partner_joined',
              displayName: room.host.name,
              timestamp: Date.now()
            });
          }
          break;
        }

        case 'offer':
        case 'answer':
        case 'ice_candidate':
        case 'ptt_status': {
          const code = payload.roomCode;
          const room = rooms.get(code);
          if (!room) return;

          const meta = clientMeta.get(ws);
          const targetWs = (meta && meta.role === 'host') ? room.guest?.ws : room.host?.ws;

          if (targetWs && targetWs.readyState === WebSocket.OPEN) {
            sendJson(targetWs, payload);
          }
          break;
        }

        case 'unpair': {
          const code = payload.roomCode;
          const room = rooms.get(code);
          if (room) {
            const meta = clientMeta.get(ws);
            const targetWs = (meta && meta.role === 'host') ? room.guest?.ws : room.host?.ws;
            if (targetWs) {
              sendJson(targetWs, { type: 'partner_left', reason: 'unpaired' });
            }
            rooms.delete(code);
            console.log(`[Unpaired] Room ${code} dissolved.`);
          }
          break;
        }

        default:
          console.warn(`[Unknown Message Type] ${type}`);
      }
    } catch (err) {
      console.error('[Message Processing Error]', err);
    }
  });

  ws.on('close', () => {
    const meta = clientMeta.get(ws);
    if (meta && meta.roomCode) {
      const room = rooms.get(meta.roomCode);
      if (room) {
        const partner = meta.role === 'host' ? room.guest : room.host;
        if (partner && partner.ws.readyState === WebSocket.OPEN) {
          sendJson(partner.ws, {
            type: 'partner_left',
            reason: 'disconnected',
            timestamp: Date.now()
          });
        }

        // If host disconnected, keep room for reconnect or cleanup if both gone
        if (meta.role === 'host') room.host = null;
        if (meta.role === 'guest') room.guest = null;

        if (!room.host && !room.guest) {
          rooms.delete(meta.roomCode);
          console.log(`[Cleanup] Empty room ${meta.roomCode} removed.`);
        }
      }
    }
  });
});

// Periodic ping interval to keep connections alive and clean stales
const heartbeatInterval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

wss.on('close', () => {
  clearInterval(heartbeatInterval);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`===============================================`);
  console.log(` Love Walkie Talkie Signaling Server Running   `);
  console.log(` Port: ${PORT}                                  `);
  console.log(` WebSocket URL: ws://localhost:${PORT}/ws      `);
  console.log(`===============================================`);
});
