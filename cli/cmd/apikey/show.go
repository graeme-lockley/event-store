package apikey

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	showUserID string
	showTenant string
)

var showCmd = &cobra.Command{
	Use:   "show <keyId>",
	Short: "Show API key details",
	Long:  `Show detailed information about a specific API key.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if showTenant != "" {
			tenantID = showTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		if showUserID == "" {
			return fmt.Errorf("user ID is required (use --user-id)")
		}

		keyID := args[0]

		apiKey, err := apiClient.GetAPIKey(tenantID, showUserID, keyID)
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
			return output.PrintAPIKeyJSON(apiKey)
		case "csv":
			return output.PrintAPIKeyCSV(apiKey)
		default:
			output.PrintAPIKey(apiKey)
			return nil
		}
	},
}

func init() {
	cmd.APIKeyCmd().AddCommand(showCmd)
	showCmd.Flags().StringVar(&showUserID, "user-id", "", "User ID (required)")
	showCmd.Flags().StringVar(&showTenant, "tenant", "", "Tenant ID (or use default)")
	showCmd.MarkFlagRequired("user-id")
}




