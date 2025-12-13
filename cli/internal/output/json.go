package output

import (
	"encoding/json"
	"os"

	"github.com/event-store/cli/internal/client"
)

// PrintJSON prints data as JSON
func PrintJSON(data interface{}) error {
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	return encoder.Encode(data)
}

// PrintTopicsListJSON prints a list of topics as JSON
func PrintTopicsListJSON(topics []client.Topic) error {
	return PrintJSON(map[string]interface{}{
		"topics": topics,
	})
}

// PrintTopicDetailsJSON prints topic details as JSON
func PrintTopicDetailsJSON(topic *client.Topic) error {
	return PrintJSON(topic)
}

// PrintConsumersListJSON prints a list of consumers as JSON
func PrintConsumersListJSON(consumers []client.Consumer) error {
	return PrintJSON(map[string]interface{}{
		"consumers": consumers,
	})
}

// PrintConsumerDetailsJSON prints consumer details as JSON
func PrintConsumerDetailsJSON(consumer *client.Consumer) error {
	return PrintJSON(consumer)
}

// PrintMessageJSON prints a message as JSON
func PrintMessageJSON(message string) error {
	return PrintJSON(map[string]string{
		"message": message,
	})
}

// PrintErrorJSON prints an error as JSON
func PrintErrorJSON(err error) error {
	return PrintJSON(map[string]string{
		"error": err.Error(),
	})
}

// PrintConsumerIDJSON prints a consumer ID as JSON
func PrintConsumerIDJSON(consumerID string) error {
	return PrintJSON(map[string]string{
		"consumerId": consumerID,
	})
}

// PrintEventsListJSON prints a list of events as JSON
func PrintEventsListJSON(events []client.Event) error {
	return PrintJSON(map[string]interface{}{
		"events": events,
	})
}

// PrintEventDetailsJSON prints event details as JSON
func PrintEventDetailsJSON(event *client.Event) error {
	return PrintJSON(event)
}

// PrintHealthJSON prints health status as JSON
func PrintHealthJSON(health *client.Health) error {
	return PrintJSON(health)
}

// PrintEventPublishResponseJSON prints event publish response as JSON
func PrintEventPublishResponseJSON(eventIDs []string) error {
	return PrintJSON(map[string]interface{}{
		"eventIds": eventIDs,
	})
}
