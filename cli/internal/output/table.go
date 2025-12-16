package output

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/event-store/cli/internal/client"
	"github.com/jedib0t/go-pretty/v6/table"
	"golang.org/x/term"
)

// shouldUseColors determines if colors should be used in output
func shouldUseColors() bool {
	// Check NO_COLOR environment variable (common convention)
	if os.Getenv("NO_COLOR") != "" {
		return false
	}

	// Check if stdout is a terminal
	if !term.IsTerminal(int(os.Stdout.Fd())) {
		return false
	}

	return true
}

// getTableStyle returns the appropriate table style based on color preference
func getTableStyle() table.Style {
	if shouldUseColors() {
		return table.StyleColoredBright
	}
	return table.StyleDefault
}

// PrintTopicsList prints a list of topics in table format
func PrintTopicsList(topics []client.Topic) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"Name", "Sequence", "Schema Count"})

	for _, topic := range topics {
		t.AppendRow(table.Row{
			topic.Name,
			strconv.Itoa(topic.Sequence),
			strconv.Itoa(len(topic.Schemas)),
		})
	}

	t.SetStyle(getTableStyle())
	t.Render()
}

// PrintTopicDetails prints detailed topic information in table format
func PrintTopicDetails(topic *client.Topic) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.SetStyle(getTableStyle())

	// Basic info
	t.AppendRow(table.Row{"Name", topic.Name})
	t.AppendRow(table.Row{"Sequence", strconv.Itoa(topic.Sequence)})
	t.AppendRow(table.Row{"Schema Count", strconv.Itoa(len(topic.Schemas))})
	t.Render()

	// Schemas
	if len(topic.Schemas) > 0 {
		fmt.Println("\nSchemas:")
		schemaTable := table.NewWriter()
		schemaTable.SetOutputMirror(os.Stdout)
		schemaTable.AppendHeader(table.Row{"Event Type", "Type", "Required Fields"})

		for _, schema := range topic.Schemas {
			required := ""
			if len(schema.Required) > 0 {
				required = fmt.Sprintf("[%s]", strings.Join(schema.Required, ", "))
			} else {
				required = "none"
			}
			schemaTable.AppendRow(table.Row{
				schema.EventType,
				schema.Type,
				required,
			})
		}

		schemaTable.SetStyle(getTableStyle())
		schemaTable.Render()
	}
}

// PrintConsumersList prints a list of consumers in table format
func PrintConsumersList(consumers []client.Consumer) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"ID", "Callback URL", "Topics"})

	for _, consumer := range consumers {
		topicsStr := ""
		if len(consumer.Topics) > 0 {
			topics := make([]string, 0, len(consumer.Topics))
			for topic, eventID := range consumer.Topics {
				if eventID == "" || eventID == "null" {
					topics = append(topics, topic)
				} else {
					topics = append(topics, fmt.Sprintf("%s:%s", topic, eventID))
				}
			}
			topicsStr = strings.Join(topics, ", ")
		} else {
			topicsStr = "none"
		}

		t.AppendRow(table.Row{
			consumer.ID,
			consumer.Callback,
			topicsStr,
		})
	}

	t.SetStyle(getTableStyle())
	t.Render()
}

// PrintConsumerDetails prints detailed consumer information in table format
func PrintConsumerDetails(consumer *client.Consumer) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.SetStyle(getTableStyle())

	t.AppendRow(table.Row{"ID", consumer.ID})
	t.AppendRow(table.Row{"Callback URL", consumer.Callback})
	t.Render()

	// Topics mapping
	if len(consumer.Topics) > 0 {
		fmt.Println("\nTopics:")
		topicsTable := table.NewWriter()
		topicsTable.SetOutputMirror(os.Stdout)
		topicsTable.AppendHeader(table.Row{"Topic", "Last Event ID"})

		for topic, eventID := range consumer.Topics {
			if eventID == "" || eventID == "null" {
				eventID = "all events"
			}
			topicsTable.AppendRow(table.Row{topic, eventID})
		}

		topicsTable.SetStyle(getTableStyle())
		topicsTable.Render()
	}
}

// PrintMessage prints a simple message
func PrintMessage(message string) {
	fmt.Println(message)
}

// PrintError prints an error message
func PrintError(err error) {
	fmt.Fprintf(os.Stderr, "Error: %s\n", err.Error())
}

// PrintEventsList prints a list of events in table format
func PrintEventsList(events []client.Event) {
	if len(events) == 0 {
		fmt.Println("No events found")
		return
	}

	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"ID", "Timestamp", "Type", "Payload"})

	for _, event := range events {
		// Format payload as compact JSON
		payloadJSON, err := json.Marshal(event.Payload)
		payloadStr := string(payloadJSON)
		if err != nil {
			payloadStr = fmt.Sprintf("%v", event.Payload)
		}
		// Truncate long payloads
		if len(payloadStr) > 100 {
			payloadStr = payloadStr[:97] + "..."
		}

		t.AppendRow(table.Row{
			event.ID,
			event.Timestamp,
			event.Type,
			payloadStr,
		})
	}

	t.SetStyle(getTableStyle())
	t.Render()
}

// PrintEventDetails prints detailed event information without truncation
func PrintEventDetails(event *client.Event) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.SetStyle(getTableStyle())

	// Basic info
	t.AppendRow(table.Row{"ID", event.ID})
	t.AppendRow(table.Row{"Timestamp", event.Timestamp})
	t.AppendRow(table.Row{"Type", event.Type})
	t.Render()

	// Payload (full, without truncation)
	fmt.Println("\nPayload:")
	payloadJSON, err := json.MarshalIndent(event.Payload, "", "  ")
	if err != nil {
		fmt.Printf("%v\n", event.Payload)
	} else {
		fmt.Println(string(payloadJSON))
	}
}

// PrintHealth prints health status in table format
func PrintHealth(health *client.Health) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.SetStyle(getTableStyle())

	t.AppendRow(table.Row{"Status", health.Status})
	t.AppendRow(table.Row{"Consumers", strconv.Itoa(health.Consumers)})

	// Format running dispatchers
	dispatchersStr := "None"
	if len(health.RunningDispatchers) > 0 {
		dispatchersStr = strings.Join(health.RunningDispatchers, ", ")
	}
	t.AppendRow(table.Row{"Running Dispatchers", dispatchersStr})

	t.Render()
}

// PrintEventPublishResponse prints event publish response in table format
func PrintEventPublishResponse(eventIDs []string) {
	if len(eventIDs) == 0 {
		fmt.Println("No events published")
		return
	}

	fmt.Printf("Published %d event(s):\n", len(eventIDs))
	for _, id := range eventIDs {
		fmt.Printf("  - %s\n", id)
	}
}
