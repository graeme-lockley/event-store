package consumer

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/event-store/cli/cmd"
	"github.com/spf13/cobra"
)

var (
	listenPort     int
	listenDataFile string
	listenSilent   bool
)

var listenCmd = &cobra.Command{
	Use:   "listen",
	Short: "Listen for consumer webhook events",
	Long: `Start an HTTP server that listens for POST requests from the event store.
All received events are logged to stdout and saved to a JSON file for inspection.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		// Only use data file if explicitly provided
		var calls []map[string]interface{}
		if listenDataFile != "" {
			// Ensure directory exists
			if err := os.MkdirAll(filepath.Dir(listenDataFile), 0755); err != nil {
				return fmt.Errorf("failed to create data directory: %w", err)
			}

			// Initialize calls file if it doesn't exist
			if data, err := os.ReadFile(listenDataFile); err == nil && len(data) > 0 {
				if err := json.Unmarshal(data, &calls); err != nil {
					// If file exists but is invalid, start fresh
					calls = []map[string]interface{}{}
				}
			}
		}

		// Create HTTP server
		mux := http.NewServeMux()

		// Health check endpoint
		mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
		})

		// Webhook endpoint - accepts POST on any path
		mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodPost {
				http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
				return
			}

			// Read request body
			body, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, "Failed to read request body", http.StatusBadRequest)
				return
			}
			defer r.Body.Close()

			// Parse JSON payload
			var payload map[string]interface{}
			if err := json.Unmarshal(body, &payload); err != nil {
				http.Error(w, "Invalid JSON", http.StatusBadRequest)
				return
			}

			// Create call record
			callRecord := map[string]interface{}{
				"path":      r.URL.Path,
				"method":    r.Method,
				"headers":   r.Header,
				"payload":   payload,
				"timestamp": time.Now().Format(time.RFC3339),
			}

			// Add to calls array
			calls = append(calls, callRecord)

			// Save to file only if data-file was specified
			if listenDataFile != "" {
				data, err := json.MarshalIndent(calls, "", "  ")
				if err != nil {
					log.Printf("Warning: failed to marshal calls: %v", err)
				} else {
					if err := os.WriteFile(listenDataFile, data, 0644); err != nil {
						log.Printf("Warning: failed to write calls file: %v", err)
					}
				}
			}

			// Echo to stdout only if not silent
			if !listenSilent {
				fmt.Printf("[%s] POST %s\n", time.Now().Format(time.RFC3339), r.URL.Path)
				payloadJSON, _ := json.MarshalIndent(payload, "", "  ")
				fmt.Println(string(payloadJSON))
				fmt.Println()
			}

			// Return success response
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
		})

		server := &http.Server{
			Addr:    fmt.Sprintf(":%d", listenPort),
			Handler: mux,
		}

		// Handle graceful shutdown
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

		go func() {
			<-sigChan
			if !listenSilent {
				fmt.Println("\nShutting down server...")
			}
			server.Close()
		}()

		if !listenSilent {
			fmt.Printf("Listening for webhook events on port %d\n", listenPort)
			if listenDataFile != "" {
				fmt.Printf("Events will be saved to: %s\n", listenDataFile)
			}
			fmt.Println("Press Ctrl+C to stop")
			fmt.Println()
		}

		// Start server
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return fmt.Errorf("server error: %w", err)
		}

		return nil
	},
}

func init() {
	cmd.ConsumerCmd().AddCommand(listenCmd)
	listenCmd.Flags().IntVarP(&listenPort, "port", "p", 19000, "Port to listen on")
	listenCmd.Flags().StringVar(&listenDataFile, "data-file", "", "File to save received events (only saves if this flag is provided)")
	listenCmd.Flags().BoolVar(&listenSilent, "silent", false, "Suppress output to stdout")
}
