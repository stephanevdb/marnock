package server

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
)

// Hub routes opaque E2E ciphertext between paired devices. It never inspects payloads.
type Hub struct {
	mu      sync.RWMutex
	clients map[string]*Client
}

type Client struct {
	deviceID  string
	authToken string
	conn      *websocket.Conn
	send      chan []byte
}

type envelope struct {
	Type    string          `json:"type"`
	ID      string          `json:"id"`
	Payload json.RawMessage `json:"payload"`
}

type registerPayload struct {
	DeviceID  string `json:"deviceId"`
	AuthToken string `json:"authToken"`
}

type forwardPayload struct {
	ToDeviceID   string `json:"toDeviceId"`
	FromDeviceID string `json:"fromDeviceId"`
	Ciphertext   string `json:"ciphertext"`
}

func NewHub() *Hub {
	return &Hub{clients: make(map[string]*Client)}
}

func (h *Hub) HandleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		OriginPatterns: []string{"*"},
	})
	if err != nil {
		log.Printf("accept: %v", err)
		return
	}

	ctx := r.Context()
	client := &Client{conn: conn, send: make(chan []byte, 64)}
	go client.writeLoop(ctx)

	defer func() {
		h.unregister(client)
		_ = conn.Close(websocket.StatusNormalClosure, "")
	}()

	for {
		_, data, err := conn.Read(ctx)
		if err != nil {
			return
		}
		msg, err := decodeFrame(data)
		if err != nil {
			log.Printf("decode: %v", err)
			continue
		}
		h.handleMessage(client, msg)
	}
}

func (h *Hub) handleMessage(c *Client, env envelope) {
	switch env.Type {
	case "relay.register":
		var p registerPayload
		if err := json.Unmarshal(env.Payload, &p); err != nil || p.DeviceID == "" {
			return
		}
		c.deviceID = p.DeviceID
		c.authToken = p.AuthToken
		h.register(c)
		resp, _ := json.Marshal(envelope{
			Type:    "pong",
			ID:      env.ID,
			Payload: json.RawMessage(`{"ok":true}`),
		})
		c.enqueue(encodeFrame(resp))
	case "relay.forward":
		var p forwardPayload
		if err := json.Unmarshal(env.Payload, &p); err != nil || p.ToDeviceID == "" {
			return
		}
		if p.FromDeviceID == "" {
			p.FromDeviceID = c.deviceID
		}
		body, _ := json.Marshal(p)
		out, _ := json.Marshal(envelope{
			Type:    "relay.forward",
			ID:      env.ID,
			Payload: body,
		})
		h.forward(p.ToDeviceID, encodeFrame(out))
	case "ping":
		resp, _ := json.Marshal(envelope{
			Type:    "pong",
			ID:      env.ID,
			Payload: json.RawMessage(`{}`),
		})
		c.enqueue(encodeFrame(resp))
	default:
		// Ignore unknown types; relay is a dumb router.
	}
}

func (h *Hub) register(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if old, ok := h.clients[c.deviceID]; ok && old != c {
		close(old.send)
	}
	h.clients[c.deviceID] = c
	log.Printf("registered device %s", c.deviceID)
}

func (h *Hub) unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if c.deviceID == "" {
		return
	}
	if cur, ok := h.clients[c.deviceID]; ok && cur == c {
		delete(h.clients, c.deviceID)
		log.Printf("unregistered device %s", c.deviceID)
	}
}

func (h *Hub) forward(to string, frame []byte) {
	h.mu.RLock()
	dst := h.clients[to]
	h.mu.RUnlock()
	if dst == nil {
		log.Printf("forward: device %s offline", to)
		return
	}
	dst.enqueue(frame)
}

func (c *Client) enqueue(frame []byte) {
	select {
	case c.send <- frame:
	default:
		log.Printf("send buffer full for %s", c.deviceID)
	}
}

func (c *Client) writeLoop(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case frame, ok := <-c.send:
			if !ok {
				return
			}
			wctx, cancel := context.WithTimeout(ctx, 10*time.Second)
			err := c.conn.Write(wctx, websocket.MessageBinary, frame)
			cancel()
			if err != nil {
				return
			}
		}
	}
}

func encodeFrame(jsonBytes []byte) []byte {
	out := make([]byte, 4+len(jsonBytes))
	binary.BigEndian.PutUint32(out[:4], uint32(len(jsonBytes)))
	copy(out[4:], jsonBytes)
	return out
}

func decodeFrame(data []byte) (envelope, error) {
	var env envelope
	if len(data) >= 4 {
		n := binary.BigEndian.Uint32(data[:4])
		if int(n)+4 == len(data) {
			data = data[4:]
		}
	}
	err := json.Unmarshal(data, &env)
	return env, err
}
