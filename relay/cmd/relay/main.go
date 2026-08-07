package main

import (
	"flag"
	"log"
	"net/http"
	"os"

	"github.com/marnock/relay/internal/server"
)

func main() {
	addr := flag.String("addr", ":8787", "listen address")
	flag.Parse()

	hub := server.NewHub()
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/ws", hub.HandleWS)

	log.Printf("Marnock relay listening on %s", *addr)
	if err := http.ListenAndServe(*addr, mux); err != nil {
		log.Println(err)
		os.Exit(1)
	}
}
