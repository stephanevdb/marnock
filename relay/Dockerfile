# Build
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/relay ./cmd/relay

# Run
FROM alpine:3.20
RUN apk add --no-cache ca-certificates wget \
  && adduser -D -H -u 65532 relay
COPY --from=build /out/relay /usr/local/bin/relay
USER relay
EXPOSE 8787
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8787/healthz || exit 1
ENTRYPOINT ["relay"]
CMD ["-addr", ":8787"]
