package tenant

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var deleteReason string

var deleteCmd = &cobra.Command{
	Use:   "delete <tenantId>",
	Short: "Delete a tenant",
	Long:  `Delete a tenant (soft delete).`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := args[0]

		err := apiClient.DeleteTenant(tenantID, deleteReason)
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

		message := fmt.Sprintf("Tenant '%s' deleted successfully", tenantID)
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
	cmd.TenantCmd().AddCommand(deleteCmd)
	deleteCmd.Flags().StringVar(&deleteReason, "reason", "", "Reason for deletion")
}

