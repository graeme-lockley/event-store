package event

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var (
	publishFile string
	publishJSON string
)

var publishCmd = &cobra.Command{
	Use:   "publish",
	Short: "Publish events to topics",
	Long: `Publish one or more events to topics in the event store.

Events can be provided via:
  - A JSON file (--file)
  - Inline JSON string (--json)

Event format:
  [
    {
      "topic": "topic-name",
      "type": "event.type",
      "payload": { ... }
    }
  ]

Examples:
  # Publish events from a file
  es event publish --file events.json

  # Publish a single event inline
  es event publish --json '[{"topic":"user-events","type":"user.created","payload":{"id":"1","name":"Alice"}}]'`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		var events []client.EventPublishRequest

		// Read events from file or JSON string
		if publishFile != "" {
			data, err := os.ReadFile(publishFile)
			if err != nil {
				return fmt.Errorf("failed to read file: %w", err)
			}
			if err := json.Unmarshal(data, &events); err != nil {
				return fmt.Errorf("failed to parse JSON file: %w", err)
			}
		} else if publishJSON != "" {
			if err := json.Unmarshal([]byte(publishJSON), &events); err != nil {
				return fmt.Errorf("failed to parse JSON: %w", err)
			}
		} else {
			return fmt.Errorf("either --file or --json must be provided")
		}

		if len(events) == 0 {
			return fmt.Errorf("at least one event must be provided")
		}

		// Publish events
		eventIDs, err := apiClient.PublishEvents(events)
		if err != nil {
			if cfg.Output.Format == "json" {
				return output.PrintErrorJSON(err)
			}
			if cfg.Output.Format == "csv" {
				return output.PrintErrorCSV(err)
			}
			output.PrintError(err)
			return err
		}

		// Output results
		switch cfg.Output.Format {
		case "json":
			return output.PrintEventPublishResponseJSON(eventIDs)
		case "csv":
			return output.PrintEventPublishResponseCSV(eventIDs)
		default:
			output.PrintEventPublishResponse(eventIDs)
			return nil
		}
	},
}

func init() {
	cmd.EventCmd().AddCommand(publishCmd)
	publishCmd.Flags().StringVar(&publishFile, "file", "", "Path to JSON file containing events")
	publishCmd.Flags().StringVar(&publishJSON, "json", "", "Inline JSON string containing events")
}

