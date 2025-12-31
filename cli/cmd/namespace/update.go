package namespace

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	updateName         string
	updateDescription  string
	updateTenant       string
	updateMetadataFile string
)

var updateCmd = &cobra.Command{
	Use:   "update <namespaceId>",
	Short: "Update a namespace",
	Long:  `Update namespace settings including name, description, and metadata.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if updateTenant != "" {
			tenantID = updateTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		namespaceID := args[0]
		req := client.NamespaceUpdateRequest{}

		if updateName != "" {
			req.Name = updateName
		}

		if updateDescription != "" {
			req.Description = updateDescription
		}

		// Load metadata from file if provided
		if updateMetadataFile != "" {
			metadataData, err := os.ReadFile(updateMetadataFile)
			if err != nil {
				return fmt.Errorf("failed to read metadata file: %w", err)
			}
			var metadata map[string]interface{}
			if err := json.Unmarshal(metadataData, &metadata); err != nil {
				return fmt.Errorf("failed to parse metadata JSON: %w", err)
			}
			req.Metadata = metadata
		}

		// Update namespace
		namespace, err := apiClient.UpdateNamespace(tenantID, namespaceID, req)
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

		message := fmt.Sprintf("Namespace '%s' updated successfully", namespace.ID)
		switch cfg.Output.Format {
		case "json":
			return output.PrintNamespaceJSON(namespace)
		case "csv":
			return output.PrintNamespaceCSV(namespace)
		default:
			output.PrintMessage(message)
			fmt.Printf("ID: %s\n", namespace.ID)
			fmt.Printf("Name: %s\n", namespace.Name)
			return nil
		}
	},
}

func init() {
	cmd.NamespaceCmd().AddCommand(updateCmd)
	updateCmd.Flags().StringVar(&updateName, "name", "", "Namespace name")
	updateCmd.Flags().StringVar(&updateDescription, "description", "", "Namespace description")
	updateCmd.Flags().StringVar(&updateTenant, "tenant", "", "Tenant ID (or use default)")
	updateCmd.Flags().StringVar(&updateMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
}



