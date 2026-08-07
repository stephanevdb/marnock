# Marnock Relay

Minimal Go WebSocket relay. Routes opaque `{ toDeviceId, ciphertext }` blobs between registered devices. No payload inspection.

## Run locally

```bash
cd relay
go run ./cmd/relay -addr :8787
```

## Docker

```bash
cd relay
docker compose up -d --build
```

- Health: `GET http://HOST:8787/healthz`
- WebSocket: `ws://HOST:8787/ws`

Change the published port with `RELAY_PORT=8787 docker compose up -d`.

Stop:

```bash
docker compose down
```

### One-shot without Compose

```bash
docker build -t marnock-relay .
docker run --rm -p 8787:8787 --name marnock-relay marnock-relay
```

On both apps, turn **off** “Local-only”, set the relay URL to `ws://YOUR_HOST:8787/ws`, and ensure devices are already paired.
