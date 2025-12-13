package health

import (
	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var showCmd = &cobra.Command{
	Use:   "show",
	Short: "Show health status",
	Long:  `Show the current health status of the event store server.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		health, err := apiClient.GetHealth()
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
			return output.PrintHealthJSON(health)
		case "csv":
			return output.PrintHealthCSV(health)
		default:
			output.PrintHealth(health)
			return nil
		}
	},
}

func init() {
	cmd.HealthCmd().AddCommand(showCmd)
}

