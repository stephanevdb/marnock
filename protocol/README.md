# Marnock Protocol

Length-prefixed JSON messages over WebSocket.

## Framing

Binary WebSocket frames:

```
[uint32 big-endian length][UTF-8 JSON bytes]
```

JSON envelope:

```json
{
  "type": "clipboard.set",
  "id": "uuid-v4",
  "payload": { }
}
```

After pairing, application messages are wrapped in `session.frame` with E2E ciphertext (XChaCha20-Poly1305). Handshake and `ping` may be plaintext on an already-authenticated LAN socket; relay always carries opaque `relay.forward` blobs.

## Message types

| Type | Direction | Purpose |
|------|-----------|---------|
| `pair.hello` | either | Start pairing (deviceId, publicKey, pairingCode) |
| `pair.complete` | either | Confirm shared key derivation |
| `ping` / `pong` | either | Liveness |
| `session.frame` | either | Encrypted inner envelope |
| `clipboard.set` / `clipboard.changed` | either | Text clipboard sync |
| `notification.posted` / `notification.removed` / `notification.action` | A→M / M→A | Mirror + actions |
| `sms.threads` / `sms.messages` / `sms.send` / `sms.received` | mixed | SMS sync |
| `call.state` / `call.history` / `call.dial` / `call.answer` / `call.reject` | mixed | Call control |
| `relay.forward` | either→relay | `{ toDeviceId, ciphertext }` opaque routing |
| `relay.register` | client→relay | `{ deviceId, authToken }` — hub requires matching pair tokens |
| `file.offer` / `file.accept` / `file.chunk` / `file.complete` / `file.cancel` | mixed | LAN or relay (E2E encrypted) file transfer (accept required) |
| `photos.list.request` / `photos.list` / `photos.get` | mixed | Camera-roll browse / pull over LAN or relay (E2E encrypted) |
| `find.ring` / `find.stop` | M→A / either | Ring misplaced phone |
| `media.command` / `media.state` | mixed | Media session control |
| `device.status` | A→M | Battery / Wi‑Fi / cellular snapshot |
| `wifi.request` / `wifi.info` | mixed | SSID (+ note); password not available to apps |
| `link.open` | either | Open URL on peer |
| `prefs.quiet` | M→A | Quiet-hours suppress outbound notifs |

## Discovery

Bonjour/NSD service type: `_marnock._tcp`

TXT records: `deviceId`, `name`, `ver`

## Crypto

1. Each device has a long-term X25519 keypair.
2. Pairing QR carries `deviceId`, `publicKey` (base64), `host`, `port`, `pairingCode`.
3. `pair.hello` / `pair.complete` exchange public keys + code; both derive `sharedSecret = X25519(private, peerPublic)`.
4. Session keys: HKDF-SHA256 over shared secret (salt `marnock`, info `session-v1`) → 32-byte key.
5. Payload encryption: ChaCha20-Poly1305 (12-byte nonce; CryptoKit / BouncyCastle compatible).
6. Relay `authToken` is `hex(sessionKey[0:16])` — opaque to the hub; used only to bind device registration and peer forward.
