package output

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/event-store/cli/internal/client"
)

// PrintTopicsListCSV prints a list of topics in CSV format
func PrintTopicsListCSV(topics []client.Topic) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"Name", "Sequence", "Schema Count"}); err != nil {
		return err
	}

	// Write rows
	for _, topic := range topics {
		row := []string{
			topic.Name,
			strconv.Itoa(topic.Sequence),
			strconv.Itoa(len(topic.Schemas)),
		}
		if err := writer.Write(row); err != nil {
			return err
		}
	}

	return nil
}

// PrintTopicDetailsCSV prints topic details in CSV format
// For single topic, we'll output it as a single row with all information
func PrintTopicDetailsCSV(topic *client.Topic) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"Name", "Sequence", "Schema Count", "Schemas"}); err != nil {
		return err
	}

	// Format schemas as JSON array
	schemasJSON, err := json.Marshal(topic.Schemas)
	schemasStr := string(schemasJSON)
	if err != nil {
		schemasStr = fmt.Sprintf("%v", topic.Schemas)
	}

	// Write row
	row := []string{
		topic.Name,
		strconv.Itoa(topic.Sequence),
		strconv.Itoa(len(topic.Schemas)),
		schemasStr,
	}
	return writer.Write(row)
}

// PrintConsumersListCSV prints a list of consumers in CSV format
func PrintConsumersListCSV(consumers []client.Consumer) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"ID", "Callback URL", "Topics"}); err != nil {
		return err
	}

	// Write rows
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
			topicsStr = strings.Join(topics, "; ")
		}

		row := []string{
			consumer.ID,
			consumer.Callback,
			topicsStr,
		}
		if err := writer.Write(row); err != nil {
			return err
		}
	}

	return nil
}

// PrintConsumerDetailsCSV prints consumer details in CSV format
func PrintConsumerDetailsCSV(consumer *client.Consumer) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"ID", "Callback URL", "Topics"}); err != nil {
		return err
	}

	// Format topics as JSON
	topicsJSON, err := json.Marshal(consumer.Topics)
	topicsStr := string(topicsJSON)
	if err != nil {
		topicsStr = fmt.Sprintf("%v", consumer.Topics)
	}

	// Write row
	row := []string{
		consumer.ID,
		consumer.Callback,
		topicsStr,
	}
	return writer.Write(row)
}

// PrintEventsListCSV prints a list of events in CSV format
func PrintEventsListCSV(events []client.Event) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"ID", "Timestamp", "Type", "Payload"}); err != nil {
		return err
	}

	// Write rows
	for _, event := range events {
		// Format payload as JSON
		payloadJSON, err := json.Marshal(event.Payload)
		payloadStr := string(payloadJSON)
		if err != nil {
			payloadStr = fmt.Sprintf("%v", event.Payload)
		}

		row := []string{
			event.ID,
			event.Timestamp,
			event.Type,
			payloadStr,
		}
		if err := writer.Write(row); err != nil {
			return err
		}
	}

	return nil
}

// PrintEventDetailsCSV prints event details in CSV format
func PrintEventDetailsCSV(event *client.Event) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	// Write header
	if err := writer.Write([]string{"ID", "Timestamp", "Type", "Payload"}); err != nil {
		return err
	}

	// Format payload as JSON
	payloadJSON, err := json.Marshal(event.Payload)
	payloadStr := string(payloadJSON)
	if err != nil {
		payloadStr = fmt.Sprintf("%v", event.Payload)
	}

	// Write row
	row := []string{
		event.ID,
		event.Timestamp,
		event.Type,
		payloadStr,
	}
	return writer.Write(row)
}

// PrintMessageCSV prints a message in CSV format (single column)
func PrintMessageCSV(message string) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	return writer.Write([]string{message})
}

// PrintErrorCSV prints an error in CSV format (single column)
func PrintErrorCSV(err error) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	return writer.Write([]string{fmt.Sprintf("Error: %s", err.Error())})
}

// PrintConsumerIDCSV prints a consumer ID in CSV format
func PrintConsumerIDCSV(consumerID string) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	if err := writer.Write([]string{"Consumer ID"}); err != nil {
		return err
	}
	return writer.Write([]string{consumerID})
}

// PrintHealthCSV prints health status as CSV
func PrintHealthCSV(health *client.Health) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	if err := writer.Write([]string{"Status", "Consumers", "Running Dispatchers"}); err != nil {
		return err
	}

	dispatchersStr := ""
	if len(health.RunningDispatchers) > 0 {
		dispatchersStr = strings.Join(health.RunningDispatchers, "; ")
	}

	return writer.Write([]string{health.Status, strconv.Itoa(health.Consumers), dispatchersStr})
}

// PrintEventPublishResponseCSV prints event publish response as CSV
func PrintEventPublishResponseCSV(eventIDs []string) error {
	writer := csv.NewWriter(os.Stdout)
	defer writer.Flush()

	if err := writer.Write([]string{"Event ID"}); err != nil {
		return err
	}

	for _, id := range eventIDs {
		if err := writer.Write([]string{id}); err != nil {
			return err
		}
	}

	return nil
}
