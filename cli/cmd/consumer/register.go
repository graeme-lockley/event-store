package consumer

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var (
	registerCallback string
	registerTopics   string
)

var registerCmd = &cobra.Command{
	Use:   "register",
	Short: "Register a new consumer",
	Long:  `Register a new consumer that will receive events from specified topics via webhook.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		if registerCallback == "" {
			return fmt.Errorf("callback URL is required (use --callback)")
		}

		if registerTopics == "" {
			return fmt.Errorf("topics are required (use --topics)")
		}

		// Parse topics string: "topic1:eventId1,topic2:null"
		topicsMap := make(map[string]string)
		topicPairs := strings.Split(registerTopics, ",")
		for _, pair := range topicPairs {
			parts := strings.SplitN(strings.TrimSpace(pair), ":", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid topic format: %s (expected 'topic:eventId' or 'topic:null')", pair)
			}

			topic := strings.TrimSpace(parts[0])
			eventID := strings.TrimSpace(parts[1])

			if topic == "" {
				return fmt.Errorf("topic name cannot be empty")
			}

			// Convert "null" string to empty string for API
			if eventID == "null" || eventID == "" {
				topicsMap[topic] = ""
			} else {
				topicsMap[topic] = eventID
			}
		}

		if len(topicsMap) == 0 {
			return fmt.Errorf("at least one topic is required")
		}

		// Register consumer
		consumerID, err := apiClient.RegisterConsumer(registerCallback, topicsMap)
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

		switch cfg.Output.Format {
		case "json":
			return output.PrintConsumerIDJSON(consumerID)
		case "csv":
			return output.PrintConsumerIDCSV(consumerID)
		default:
			output.PrintMessage(fmt.Sprintf("Consumer registered with ID: %s", consumerID))
			return nil
		}
	},
}

func init() {
	cmd.ConsumerCmd().AddCommand(registerCmd)
	registerCmd.Flags().StringVar(&registerCallback, "callback", "", "Callback URL for webhook delivery (required)")
	registerCmd.Flags().StringVar(&registerTopics, "topics", "", "Topics mapping in format 'topic1:eventId1,topic2:null' (required)")
	registerCmd.MarkFlagRequired("callback")
	registerCmd.MarkFlagRequired("topics")
}
