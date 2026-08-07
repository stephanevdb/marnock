package server

import (
	"encoding/json"
	"testing"
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
