package consumer

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var showCmd = &cobra.Command{
	Use:   "show <id>",
	Short: "Show detailed information about a consumer",
	Long:  `Show detailed information about a specific consumer, including its callback URL and subscribed topics.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		consumerID := args[0]

		// Get all consumers and find the one we want
		consumers, err := apiClient.GetConsumers()
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

		var consumer *client.Consumer
		for i := range consumers {
			if consumers[i].ID == consumerID {
				consumer = &consumers[i]
				break
			}
		}

		if consumer == nil {
			err := fmt.Errorf("consumer '%s' not found", consumerID)
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
			return output.PrintConsumerDetailsJSON(consumer)
		case "csv":
			return output.PrintConsumerDetailsCSV(consumer)
		default:
			output.PrintConsumerDetails(consumer)
			return nil
		}
	},
}

func init() {
	cmd.ConsumerCmd().AddCommand(showCmd)
}
