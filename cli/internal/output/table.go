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

// PrintTenant prints a tenant in table format
func PrintTenant(tenant *client.Tenant) {
	fmt.Printf("ID: %s\n", tenant.ID)
	fmt.Printf("Name: %s\n", tenant.Name)
	fmt.Printf("Created At: %s\n", tenant.CreatedAt)
	if tenant.UpdatedAt != "" {
		fmt.Printf("Updated At: %s\n", tenant.UpdatedAt)
	}
	if tenant.DeletedAt != "" {
		fmt.Printf("Deleted At: %s\n", tenant.DeletedAt)
	}
	if tenant.Quota != nil {
		fmt.Printf("Quota:\n")
		fmt.Printf("  Max Topics: %d\n", tenant.Quota.MaxTopics)
		fmt.Printf("  Max Namespaces: %d\n", tenant.Quota.MaxNamespaces)
		fmt.Printf("  Max Events Per Day: %d\n", tenant.Quota.MaxEventsPerDay)
		fmt.Printf("  Max Consumers: %d\n", tenant.Quota.MaxConsumers)
		fmt.Printf("  Max Users: %d\n", tenant.Quota.MaxUsers)
	}
}

// PrintTenantsList prints a list of tenants in table format
func PrintTenantsList(tenants []client.Tenant) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"ID", "Name", "Created At"})
	t.SetStyle(getTableStyle())

	for _, tenant := range tenants {
		t.AppendRow(table.Row{
			tenant.ID,
			tenant.Name,
			tenant.CreatedAt,
		})
	}

	t.Render()
}

// PrintNamespace prints a namespace in table format
func PrintNamespace(namespace *client.Namespace) {
	fmt.Printf("Tenant ID: %s\n", namespace.TenantID)
	fmt.Printf("ID: %s\n", namespace.ID)
	fmt.Printf("Name: %s\n", namespace.Name)
	if namespace.Description != "" {
		fmt.Printf("Description: %s\n", namespace.Description)
	}
	fmt.Printf("Created At: %s\n", namespace.CreatedAt)
	if namespace.UpdatedAt != "" {
		fmt.Printf("Updated At: %s\n", namespace.UpdatedAt)
	}
	if namespace.DeletedAt != "" {
		fmt.Printf("Deleted At: %s\n", namespace.DeletedAt)
	}
}

// PrintNamespacesList prints a list of namespaces in table format
func PrintNamespacesList(namespaces []client.Namespace) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"Tenant ID", "ID", "Name", "Description", "Created At"})
	t.SetStyle(getTableStyle())

	for _, namespace := range namespaces {
		t.AppendRow(table.Row{
			namespace.TenantID,
			namespace.ID,
			namespace.Name,
			namespace.Description,
			namespace.CreatedAt,
		})
	}

	t.Render()
}

// PrintUser prints a user in table format
func PrintUser(user *client.User) {
	fmt.Printf("ID: %s\n", user.ID)
	fmt.Printf("Email: %s\n", user.Email)
	fmt.Printf("Name: %s\n", user.Name)
	fmt.Printf("Status: %s\n", user.Status)
	fmt.Printf("Created At: %s\n", user.CreatedAt)
	if user.UpdatedAt != "" {
		fmt.Printf("Updated At: %s\n", user.UpdatedAt)
	}
	if user.LastLoginAt != "" {
		fmt.Printf("Last Login At: %s\n", user.LastLoginAt)
	}
	fmt.Printf("Email Verified: %v\n", user.EmailVerified)
	if user.PrimaryTenantID != "" {
		fmt.Printf("Primary Tenant ID: %s\n", user.PrimaryTenantID)
	}
}

// PrintUsersList prints a list of users in table format
func PrintUsersList(users []client.User) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"ID", "Email", "Name", "Status", "Created At"})
	t.SetStyle(getTableStyle())

	for _, user := range users {
		t.AppendRow(table.Row{
			user.ID,
			user.Email,
			user.Name,
			user.Status,
			user.CreatedAt,
		})
	}

	t.Render()
}

// PrintAPIKey prints an API key in table format
func PrintAPIKey(apiKey *client.APIKey) {
	fmt.Printf("ID: %s\n", apiKey.ID)
	fmt.Printf("User ID: %s\n", apiKey.UserID)
	fmt.Printf("Name: %s\n", apiKey.Name)
	if apiKey.Description != "" {
		fmt.Printf("Description: %s\n", apiKey.Description)
	}
	fmt.Printf("Created At: %s\n", apiKey.CreatedAt)
	if apiKey.ExpiresAt != "" {
		fmt.Printf("Expires At: %s\n", apiKey.ExpiresAt)
	}
	if apiKey.LastUsedAt != "" {
		fmt.Printf("Last Used At: %s\n", apiKey.LastUsedAt)
	}
	if apiKey.RevokedAt != "" {
		fmt.Printf("Revoked At: %s\n", apiKey.RevokedAt)
	}
	fmt.Printf("Is Active: %v\n", apiKey.IsActive)
	if apiKey.Key != "" {
		fmt.Printf("\nAPI Key: %s\n", apiKey.Key)
	}
}

// PrintAPIKeysList prints a list of API keys in table format
func PrintAPIKeysList(apiKeys []client.APIKey) {
	t := table.NewWriter()
	t.SetOutputMirror(os.Stdout)
	t.AppendHeader(table.Row{"ID", "User ID", "Name", "Description", "Created At", "Expires At", "Is Active"})
	t.SetStyle(getTableStyle())

	for _, apiKey := range apiKeys {
		t.AppendRow(table.Row{
			apiKey.ID,
			apiKey.UserID,
			apiKey.Name,
			apiKey.Description,
			apiKey.CreatedAt,
			apiKey.ExpiresAt,
			strconv.FormatBool(apiKey.IsActive),
		})
	}

	t.Render()
}
