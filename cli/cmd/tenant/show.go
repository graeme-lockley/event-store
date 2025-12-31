package tenant

import (
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var showCmd = &cobra.Command{
	Use:   "show <tenantId>",
	Short: "Show tenant details",
	Long:  `Show detailed information about a specific tenant.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := args[0]

		tenant, err := apiClient.GetTenant(tenantID)
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
			return output.PrintTenantJSON(tenant)
		case "csv":
			return output.PrintTenantCSV(tenant)
		default:
			output.PrintTenant(tenant)
			return nil
		}
	},
}

func init() {
	cmd.TenantCmd().AddCommand(showCmd)
}



