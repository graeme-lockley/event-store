package apikey

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	listUserID string
	listTenant string
)

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List API keys",
	Long:  `List API keys for a user.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if listTenant != "" {
			tenantID = listTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		if listUserID == "" {
			return fmt.Errorf("user ID is required (use --user-id)")
		}

		apiKeys, err := apiClient.ListAPIKeys(tenantID, listUserID)
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
			return output.PrintAPIKeysListJSON(apiKeys)
		case "csv":
			return output.PrintAPIKeysListCSV(apiKeys)
		default:
			output.PrintAPIKeysList(apiKeys)
			return nil
		}
	},
}

func init() {
	cmd.APIKeyCmd().AddCommand(listCmd)
	listCmd.Flags().StringVar(&listUserID, "user-id", "", "User ID (required)")
	listCmd.Flags().StringVar(&listTenant, "tenant", "", "Tenant ID (or use default)")
	listCmd.MarkFlagRequired("user-id")
}




