package topic

import (
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var showCmd = &cobra.Command{
	Use:   "show <name>",
	Short: "Show detailed information about a topic",
	Long:  `Show detailed information about a specific topic, including its schemas.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		topic, err := apiClient.GetTopic(args[0])
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
			return output.PrintTopicDetailsJSON(topic)
		case "csv":
			return output.PrintTopicDetailsCSV(topic)
		default:
			output.PrintTopicDetails(topic)
			return nil
		}
	},
}

func init() {
	cmd.TopicCmd().AddCommand(showCmd)
}
