package server

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

func TestTryRegisterRequiresMatchingToken(t *testing.T) {
	h := NewHub()
	a := &Client{send: make(chan []byte, 4)}
	if !h.tryRegister(a, "dev-a", "token-pair") {
		t.Fatal("first register should succeed")
	}

	b := &Client{send: make(chan []byte, 4)}
	if h.tryRegister(b, "dev-a", "other-token") {
		t.Fatal("takeover with different token must fail")
	}
	if h.clients["dev-a"] != a {
		t.Fatal("original client should remain registered")
	}

	c := &Client{send: make(chan []byte, 4)}
	if !h.tryRegister(c, "dev-a", "token-pair") {
		t.Fatal("reconnect with same token should succeed")
	}
	if h.clients["dev-a"] != c {
		t.Fatal("reconnect should replace client")
	}
}

func TestStickyTokenAfterUnregister(t *testing.T) {
	h := NewHub()
	a := &Client{send: make(chan []byte, 4)}
	if !h.tryRegister(a, "dev-a", "token-pair") {
		t.Fatal("register failed")
	}
	h.unregister(a)

	thief := &Client{send: make(chan []byte, 4)}
	if h.tryRegister(thief, "dev-a", "evil") {
		t.Fatal("sticky token should block reclaim with wrong token")
	}

	ok := &Client{send: make(chan []byte, 4)}
	if !h.tryRegister(ok, "dev-a", "token-pair") {
		t.Fatal("same token should reclaim after disconnect")
	}
}

func TestForwardRequiresMatchingPeerTokens(t *testing.T) {
	h := NewHub()
	mac := &Client{send: make(chan []byte, 4)}
	phone := &Client{send: make(chan []byte, 4)}
	intruder := &Client{send: make(chan []byte, 4)}
	if !h.tryRegister(mac, "mac", "pair-token") {
		t.Fatal("mac register")
	}
	if !h.tryRegister(phone, "phone", "pair-token") {
		t.Fatal("phone register")
	}
	if !h.tryRegister(intruder, "other", "different") {
		t.Fatal("intruder register")
	}

	frame := encodeFrame([]byte(`{"type":"relay.forward","id":"1","payload":{}}`))
	h.forward(intruder, "phone", frame)
	select {
	case <-phone.send:
		t.Fatal("forward from mismatched token must not deliver")
	default:
	}

	h.forward(mac, "phone", frame)
	select {
	case got := <-phone.send:
		if len(got) == 0 {
			t.Fatal("empty frame")
		}
	default:
		t.Fatal("matching pair forward should deliver")
	}
}

func TestHandleRegisterEmptyToken(t *testing.T) {
	h := NewHub()
	c := &Client{send: make(chan []byte, 4)}
	payload, _ := json.Marshal(registerPayload{DeviceID: "x", AuthToken: ""})
	h.handleMessage(c, envelope{
		Type:    "relay.register",
		ID:      "1",
		Payload: payload,
	})
	select {
	case frame := <-c.send:
		env, err := decodeFrame(frame)
		if err != nil {
			t.Fatal(err)
		}
		var p map[string]any
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			t.Fatal(err)
		}
		if p["ok"] != false {
			t.Fatalf("expected ok=false, got %v", p)
		}
	default:
		t.Fatal("expected register error reply")
	}
	if c.deviceID != "" {
		t.Fatal("client should not be registered")
	}
}

func TestForwardRejectsSpoofedFrom(t *testing.T) {
	h := NewHub()
	mac := &Client{send: make(chan []byte, 4)}
	phone := &Client{send: make(chan []byte, 4)}
	_ = h.tryRegister(mac, "mac", "pair")
	_ = h.tryRegister(phone, "phone", "pair")

	payload, _ := json.Marshal(forwardPayload{
		ToDeviceID:   "phone",
		FromDeviceID: "someone-else",
		Ciphertext:   "abc",
	})
	h.handleMessage(mac, envelope{Type: "relay.forward", ID: "1", Payload: payload})
	select {
	case <-phone.send:
		t.Fatal("spoofed fromDeviceId must not forward")
	default:
	}
}

func TestLargeFramedMessageAccepted(t *testing.T) {
	h := NewHub()
	srv := httptest.NewServer(http.HandlerFunc(h.HandleWS))
	t.Cleanup(srv.Close)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	t.Cleanup(cancel)

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	mac, _, err := websocket.Dial(ctx, wsURL, nil)
	if err != nil {
		t.Fatalf("dial mac: %v", err)
	}
	defer mac.Close(websocket.StatusNormalClosure, "")
	mac.SetReadLimit(2 << 20)
	phone, _, err := websocket.Dial(ctx, wsURL, nil)
	if err != nil {
		t.Fatalf("dial phone: %v", err)
	}
	defer phone.Close(websocket.StatusNormalClosure, "")
	phone.SetReadLimit(2 << 20)

	register := func(c *websocket.Conn, deviceID string) {
		body, _ := json.Marshal(registerPayload{DeviceID: deviceID, AuthToken: "pair"})
		msg, _ := json.Marshal(envelope{Type: "relay.register", ID: "r", Payload: body})
		if err := c.Write(ctx, websocket.MessageBinary, encodeFrame(msg)); err != nil {
			t.Fatalf("register write %s: %v", deviceID, err)
		}
		_, _, err := c.Read(ctx)
		if err != nil {
			t.Fatalf("register read %s: %v", deviceID, err)
		}
	}
	register(mac, "mac")
	register(phone, "phone")

	// ~100 KiB ciphertext inside a framed relay.forward (well above the old 32 KiB default).
	big := strings.Repeat("A", 100*1024)
	payload, _ := json.Marshal(forwardPayload{
		ToDeviceID:   "phone",
		FromDeviceID: "mac",
		Ciphertext:   big,
	})
	out, _ := json.Marshal(envelope{Type: "relay.forward", ID: "fwd", Payload: payload})
	frame := encodeFrame(out)
	if len(frame) < 100*1024 {
		t.Fatalf("expected large frame, got %d bytes", len(frame))
	}
	if err := mac.Write(ctx, websocket.MessageBinary, frame); err != nil {
		t.Fatalf("write large frame: %v", err)
	}

	_, data, err := phone.Read(ctx)
	if err != nil {
		t.Fatalf("phone did not receive large frame: %v", err)
	}
	env, err := decodeFrame(data)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	if env.Type != "relay.forward" {
		t.Fatalf("type=%s", env.Type)
	}
	var got forwardPayload
	if err := json.Unmarshal(env.Payload, &got); err != nil {
		t.Fatal(err)
	}
	if len(got.Ciphertext) != len(big) {
		t.Fatalf("ciphertext len=%d want %d", len(got.Ciphertext), len(big))
	}
}
