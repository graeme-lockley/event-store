package apikey

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	revokeUserID string
	revokeTenant string
)

var revokeCmd = &cobra.Command{
	Use:   "revoke <keyId>",
	Short: "Revoke an API key",
	Long:  `Revoke an API key. The key will no longer be valid for authentication.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if revokeTenant != "" {
			tenantID = revokeTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		if revokeUserID == "" {
			return fmt.Errorf("user ID is required (use --user-id)")
		}

		keyID := args[0]

		err := apiClient.RevokeAPIKey(tenantID, revokeUserID, keyID)
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

		message := fmt.Sprintf("API key '%s' revoked successfully", keyID)
		switch cfg.Output.Format {
		case "json":
			return output.PrintMessageJSON(message)
		case "csv":
			return output.PrintMessageCSV(message)
		default:
			output.PrintMessage(message)
			return nil
		}
	},
}

func init() {
	cmd.APIKeyCmd().AddCommand(revokeCmd)
	revokeCmd.Flags().StringVar(&revokeUserID, "user-id", "", "User ID (required)")
	revokeCmd.Flags().StringVar(&revokeTenant, "tenant", "", "Tenant ID (or use default)")
	revokeCmd.MarkFlagRequired("user-id")
}



