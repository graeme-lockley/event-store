package event

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var showCmd = &cobra.Command{
	Use:   "show <topic> <event-id>",
	Short: "Show detailed information about an event",
	Long: `Show detailed information about a specific event, including the full payload without truncation.

Examples:
  # Show an event by ID
  es event show user-events user-events-10

  # Show an event in JSON format
  es event show user-events user-events-10 --output json`,
	Args: cobra.ExactArgs(2),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		topic := args[0]
		eventID := args[1]

		// Get events starting from the event before the requested one
		// We'll fetch a small batch and find the specific event
		query := &client.EventsQuery{
			Limit: 100, // Fetch a reasonable batch to find the event
		}

		events, err := apiClient.GetEvents(topic, query)
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

		// Find the specific event
		var foundEvent *client.Event
		for i := range events {
			if events[i].ID == eventID {
				foundEvent = &events[i]
				break
			}
		}

		if foundEvent == nil {
			// Try fetching more events or using sinceEventId
			// Extract sequence from event ID (format: topic-sequence)
			// For now, let's try a different approach - fetch from the beginning
			// with a larger limit
			query.Limit = 10000
			allEvents, err := apiClient.GetEvents(topic, query)
			if err != nil {
				err := fmt.Errorf("event '%s' not found in topic '%s'", eventID, topic)
				if cfg.Output.Format == "json" {
					return output.PrintErrorJSON(err)
				}
				if cfg.Output.Format == "csv" {
					return output.PrintErrorCSV(err)
				}
				output.PrintError(err)
				return err
			}

			for i := range allEvents {
				if allEvents[i].ID == eventID {
					foundEvent = &allEvents[i]
					break
				}
			}

			if foundEvent == nil {
				err := fmt.Errorf("event '%s' not found in topic '%s'", eventID, topic)
				if cfg.Output.Format == "json" {
					return output.PrintErrorJSON(err)
				}
				if cfg.Output.Format == "csv" {
					return output.PrintErrorCSV(err)
				}
				output.PrintError(err)
				return err
			}
		}

		switch cfg.Output.Format {
		case "json":
			return output.PrintEventDetailsJSON(foundEvent)
		case "csv":
			return output.PrintEventDetailsCSV(foundEvent)
		default:
			output.PrintEventDetails(foundEvent)
			return nil
		}
	},
}

func init() {
	cmd.EventCmd().AddCommand(showCmd)
}

