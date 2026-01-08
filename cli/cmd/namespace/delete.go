package namespace

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	deleteTenant string
	deleteReason string
)

var deleteCmd = &cobra.Command{
	Use:   "delete <namespaceId>",
	Short: "Delete a namespace",
	Long:  `Delete a namespace (soft delete).`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if deleteTenant != "" {
			tenantID = deleteTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		namespaceID := args[0]

		err := apiClient.DeleteNamespace(tenantID, namespaceID, deleteReason)
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

		message := fmt.Sprintf("Namespace '%s' deleted successfully", namespaceID)
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
	cmd.NamespaceCmd().AddCommand(deleteCmd)
	deleteCmd.Flags().StringVar(&deleteTenant, "tenant", "", "Tenant ID (or use default)")
	deleteCmd.Flags().StringVar(&deleteReason, "reason", "", "Reason for deletion")
}