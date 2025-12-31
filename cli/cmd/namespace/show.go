package namespace

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var showTenant string

var showCmd = &cobra.Command{
	Use:   "show <namespaceId>",
	Short: "Show namespace details",
	Long:  `Show detailed information about a specific namespace.`,
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

		namespaceID := args[0]

		namespace, err := apiClient.GetNamespace(tenantID, namespaceID)
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
			return output.PrintNamespaceJSON(namespace)
		case "csv":
			return output.PrintNamespaceCSV(namespace)
		default:
			output.PrintNamespace(namespace)
			return nil
		}
	},
}

func init() {
	cmd.NamespaceCmd().AddCommand(showCmd)
	showCmd.Flags().StringVar(&showTenant, "tenant", "", "Tenant ID (or use default)")
}



