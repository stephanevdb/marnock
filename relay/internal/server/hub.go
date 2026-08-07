package server

import (
	"context"
	"crypto/subtle"
	"encoding/binary"
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
)

// Hub routes opaque E2E ciphertext between paired devices. It never inspects payloads.
// authToken (shared by a pair) binds deviceId registration and peer-to-peer forward.
type Hub struct {
	mu      sync.RWMutex
	clients map[string]*Client
	// tokens remembers the last successful authToken per deviceId after disconnect
	// so offline reclaim requires the same token.
	tokens map[string]string
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
	return &Hub{
		clients: make(map[string]*Client),
		tokens:  make(map[string]string),
	}
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
	// File/photo chunks are ~48KiB raw and expand with base64 + envelopes; default 32KiB is too small.
	conn.SetReadLimit(2 << 20)
	client := &Client{conn: conn, send: make(chan []byte, 256)}
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
			h.replyRegister(c, env.ID, false, "invalid payload")
			return
		}
		if p.AuthToken == "" {
			h.replyRegister(c, env.ID, false, "authToken required")
			return
		}
		if !h.tryRegister(c, p.DeviceID, p.AuthToken) {
			h.replyRegister(c, env.ID, false, "auth rejected")
			return
		}
		h.replyRegister(c, env.ID, true, "")
	case "relay.forward":
		var p forwardPayload
		if err := json.Unmarshal(env.Payload, &p); err != nil || p.ToDeviceID == "" {
			return
		}
		if c.deviceID == "" || c.authToken == "" {
			return
		}
		if p.FromDeviceID == "" {
			p.FromDeviceID = c.deviceID
		}
		if p.FromDeviceID != c.deviceID {
			return
		}
		if p.Ciphertext == "" {
			return
		}
		body, _ := json.Marshal(p)
		out, _ := json.Marshal(envelope{
			Type:    "relay.forward",
			ID:      env.ID,
			Payload: body,
		})
		h.forward(c, p.ToDeviceID, encodeFrame(out))
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

func (h *Hub) replyRegister(c *Client, id string, ok bool, errMsg string) {
	payload := map[string]any{"ok": ok}
	if errMsg != "" {
		payload["error"] = errMsg
	}
	raw, _ := json.Marshal(payload)
	resp, _ := json.Marshal(envelope{
		Type:    "pong",
		ID:      id,
		Payload: raw,
	})
	c.enqueue(encodeFrame(resp))
}

func tokenEqual(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

// tryRegister binds c to deviceID if the authToken is allowed.
func (h *Hub) tryRegister(c *Client, deviceID, authToken string) bool {
	h.mu.Lock()
	defer h.mu.Unlock()

	if sticky, ok := h.tokens[deviceID]; ok && !tokenEqual(sticky, authToken) {
		log.Printf("register rejected for %s: sticky token mismatch", deviceID)
		return false
	}
	if old, ok := h.clients[deviceID]; ok && old != c {
		if !tokenEqual(old.authToken, authToken) {
			log.Printf("register rejected for %s: online token mismatch", deviceID)
			return false
		}
		close(old.send)
	}

	c.deviceID = deviceID
	c.authToken = authToken
	h.clients[deviceID] = c
	h.tokens[deviceID] = authToken
	log.Printf("registered device %s", deviceID)
	return true
}

func (h *Hub) unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if c.deviceID == "" {
		return
	}
	if cur, ok := h.clients[c.deviceID]; ok && cur == c {
		delete(h.clients, c.deviceID)
		// Keep h.tokens[deviceID] so reclaim needs the same authToken.
		log.Printf("unregistered device %s", c.deviceID)
	}
}

func (h *Hub) forward(from *Client, to string, frame []byte) {
	h.mu.RLock()
	dst := h.clients[to]
	h.mu.RUnlock()
	if dst == nil {
		log.Printf("forward: device %s offline", to)
		return
	}
	if !tokenEqual(from.authToken, dst.authToken) {
		log.Printf("forward: token mismatch %s -> %s", from.deviceID, to)
		return
	}
	dst.enqueue(frame)
}

func (c *Client) enqueue(frame []byte) {
	select {
	case c.send <- frame:
		return
	default:
	}
	// Brief block so bursty file chunks are less likely to be dropped.
	timer := time.NewTimer(250 * time.Millisecond)
	defer timer.Stop()
	select {
	case c.send <- frame:
	case <-timer.C:
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
