package event

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var (
	listFromEventID string
	listLimit       int
	listDate        string
	listFilter      string
)

var listCmd = &cobra.Command{
	Use:   "list <topic>",
	Short: "List events from a topic",
	Long: `List events from a topic with optional filtering and pagination.

Examples:
  # List all events from a topic
  es event list user-events

  # List events starting from a specific event ID
  es event list user-events --from-event-id user-events-10

  # List up to 50 events
  es event list user-events --limit 50

  # List events from a specific date
  es event list user-events --date 2025-01-15

  # Filter events by type
  es event list user-events --filter "type:user.created"

  # Filter events by payload field
  es event list user-events --filter "payload.email:alice@example.com"`,
	Args: cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		topic := args[0]

		// If filtering is enabled, we need to fetch more events to ensure we get
		// the requested number after filtering. Multiply by a factor to account for filtering.
		apiLimit := listLimit
		if listFilter != "" && listLimit > 0 {
			// Fetch more events when filtering to ensure we get enough after filtering
			// Use a multiplier (e.g., 5x) to account for filter selectivity
			apiLimit = listLimit * 5
		}

		// Build query
		query := &client.EventsQuery{
			SinceEventID: listFromEventID,
			Date:         listDate,
			Limit:        apiLimit,
		}

		// Get events
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

		// Apply filter if provided
		if listFilter != "" {
			events = filterEvents(events, listFilter)
		}

		// Apply limit after filtering to ensure we get exactly the requested number
		if listLimit > 0 && len(events) > listLimit {
			events = events[:listLimit]
		}

		switch cfg.Output.Format {
		case "json":
			return output.PrintEventsListJSON(events)
		case "csv":
			return output.PrintEventsListCSV(events)
		default:
			output.PrintEventsList(events)
			return nil
		}
	},
}

// filterEvents applies client-side filtering to events
func filterEvents(events []client.Event, filter string) []client.Event {
	if filter == "" {
		return events
	}

	filtered := make([]client.Event, 0)

	for _, event := range events {
		if matchesFilter(event, filter) {
			filtered = append(filtered, event)
		}
	}

	return filtered
}

// matchesFilter checks if an event matches the filter criteria
func matchesFilter(event client.Event, filter string) bool {
	// Parse filter format: "field:value" or "field.path:value"
	parts := strings.SplitN(filter, ":", 2)
	if len(parts) != 2 {
		return false
	}

	field := strings.TrimSpace(parts[0])
	value := strings.TrimSpace(parts[1])

	// Handle different field types
	switch {
	case field == "type":
		return event.Type == value
	case field == "id":
		return event.ID == value
	case strings.HasPrefix(field, "payload."):
		// Extract payload field path (e.g., "payload.email" -> "email")
		payloadPath := strings.TrimPrefix(field, "payload.")
		return matchesPayloadField(event.Payload, payloadPath, value)
	default:
		// Try as direct payload field
		return matchesPayloadField(event.Payload, field, value)
	}
}

// matchesPayloadField checks if a payload field matches the value
func matchesPayloadField(payload map[string]interface{}, path, value string) bool {
	// Handle nested paths (e.g., "user.email")
	parts := strings.Split(path, ".")
	current := payload

	for i, part := range parts {
		val, ok := current[part]
		if !ok {
			return false
		}

		// If this is the last part, compare the value
		if i == len(parts)-1 {
			// Convert to string for comparison
			valStr := fmt.Sprintf("%v", val)
			return valStr == value
		}

		// Navigate deeper into nested objects
		if nested, ok := val.(map[string]interface{}); ok {
			current = nested
		} else {
			return false
		}
	}

	return false
}

func init() {
	cmd.EventCmd().AddCommand(listCmd)
	listCmd.Flags().StringVar(&listFromEventID, "from-event-id", "", "Get events after this event ID")
	listCmd.Flags().IntVar(&listLimit, "limit", 0, "Maximum number of events to return (0 = no limit)")
	listCmd.Flags().StringVar(&listDate, "date", "", "Get events from a specific date (YYYY-MM-DD)")
	listCmd.Flags().StringVar(&listFilter, "filter", "", "Filter events (format: 'field:value', e.g., 'type:user.created' or 'payload.email:alice@example.com')")
}

