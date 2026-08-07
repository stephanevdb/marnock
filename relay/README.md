# Marnock Relay

Minimal Go WebSocket relay. Routes opaque `{ toDeviceId, ciphertext }` blobs between registered devices. No payload inspection.

## Run locally

```bash
cd relay
go run ./cmd/relay -addr :8787
```

## Docker

Pulls `ghcr.io/stephanevdb/marnock-relay:latest` (published by CI on `main` / `v*` tags):

```bash
cd relay
docker compose pull
docker compose up -d
```

- Health: `GET /` or `GET /healthz` → `ok`
- WebSocket: `ws://HOST:8787/ws` (or `wss://mardock.stephanevdb.com/ws` via Pangolin)

Change the published port with `RELAY_PORT=8787 docker compose up -d`.

Stop:

```bash
docker compose down
```

Build locally instead of GHCR:

```bash
docker build -t marnock-relay .
docker run --rm -p 8787:8787 --name marnock-relay marnock-relay
```

The GHCR package may be private until you set it public under GitHub → Packages (or `docker login ghcr.io`).

On both apps, turn **off** “Local-only”, set the relay URL to `ws://YOUR_HOST:8787/ws`, and ensure devices are already paired.
