package consumer

import (
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List all consumers",
	Long:  `List all registered consumers in the event store.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

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

		switch cfg.Output.Format {
		case "json":
			return output.PrintConsumersListJSON(consumers)
		case "csv":
			return output.PrintConsumersListCSV(consumers)
		default:
			output.PrintConsumersList(consumers)
			return nil
		}
	},
}

func init() {
	cmd.ConsumerCmd().AddCommand(listCmd)
}
