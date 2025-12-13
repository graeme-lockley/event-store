package topic

import (
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List all topics",
	Long:  `List all topics in the event store.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		topics, err := apiClient.GetTopics()
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
			return output.PrintTopicsListJSON(topics)
		case "csv":
			return output.PrintTopicsListCSV(topics)
		default:
			output.PrintTopicsList(topics)
			return nil
		}
	},
}

func init() {
	cmd.TopicCmd().AddCommand(listCmd)
}
