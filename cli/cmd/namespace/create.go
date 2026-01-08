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
	createID          string
	createName        string
	createDescription string
	createTenant      string
	createMetadataFile string
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new namespace",
	Long:  `Create a new namespace in a tenant.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if createTenant != "" {
			tenantID = createTenant
		}
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant or set default with 'es context set --tenant <id>')")
		}

		if createID == "" {
			return fmt.Errorf("namespace ID is required (use --id)")
		}
		if createName == "" {
			return fmt.Errorf("namespace name is required (use --name)")
		}

		req := client.NamespaceCreateRequest{
			ID:   createID,
			Name: createName,
		}

		if createDescription != "" {
			req.Description = createDescription
		}

		// Load metadata from file if provided
		if createMetadataFile != "" {
			metadataData, err := os.ReadFile(createMetadataFile)
			if err != nil {
				return fmt.Errorf("failed to read metadata file: %w", err)
			}
			var metadata map[string]interface{}
			if err := json.Unmarshal(metadataData, &metadata); err != nil {
				return fmt.Errorf("failed to parse metadata JSON: %w", err)
			}
			req.Metadata = metadata
		}

		// Create namespace
		namespace, err := apiClient.CreateNamespace(tenantID, req)
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

		message := fmt.Sprintf("Namespace '%s' created successfully", namespace.ID)
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
	cmd.NamespaceCmd().AddCommand(createCmd)
	createCmd.Flags().StringVar(&createID, "id", "", "Namespace ID (required)")
	createCmd.Flags().StringVar(&createName, "name", "", "Namespace name (required)")
	createCmd.Flags().StringVar(&createDescription, "description", "", "Namespace description")
	createCmd.Flags().StringVar(&createTenant, "tenant", "", "Tenant ID (or use default)")
	createCmd.Flags().StringVar(&createMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
	createCmd.MarkFlagRequired("id")
	createCmd.MarkFlagRequired("name")
}




