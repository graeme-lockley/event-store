package namespace

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var listTenant string

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List namespaces",
	Long:  `List namespaces in a tenant.`,
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

		namespaces, err := apiClient.ListNamespaces(tenantID)
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
			return output.PrintNamespacesListJSON(namespaces)
		case "csv":
			return output.PrintNamespacesListCSV(namespaces)
		default:
			output.PrintNamespacesList(namespaces)
			return nil
		}
	},
}

func init() {
	cmd.NamespaceCmd().AddCommand(listCmd)
	listCmd.Flags().StringVar(&listTenant, "tenant", "", "Tenant ID (or use default)")
}



