package tenant

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
	createID         string
	createName       string
	createQuotaFile  string
	createMetadataFile string
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new tenant",
	Long:  `Create a new tenant with optional quota and metadata settings.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		if createID == "" {
			return fmt.Errorf("tenant ID is required (use --id)")
		}
		if createName == "" {
			return fmt.Errorf("tenant name is required (use --name)")
		}

		req := client.TenantCreateRequest{
			ID:   createID,
			Name: createName,
		}

		// Load quota from file if provided
		if createQuotaFile != "" {
			quotaData, err := os.ReadFile(createQuotaFile)
			if err != nil {
				return fmt.Errorf("failed to read quota file: %w", err)
			}
			var quota client.Quota
			if err := json.Unmarshal(quotaData, &quota); err != nil {
				return fmt.Errorf("failed to parse quota JSON: %w", err)
			}
			req.Quota = &quota
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

		// Create tenant
		tenant, err := apiClient.CreateTenant(req)
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

		message := fmt.Sprintf("Tenant '%s' created successfully", tenant.ID)
		switch cfg.Output.Format {
		case "json":
			return output.PrintTenantJSON(tenant)
		case "csv":
			return output.PrintTenantCSV(tenant)
		default:
			output.PrintMessage(message)
			fmt.Printf("ID: %s\n", tenant.ID)
			fmt.Printf("Name: %s\n", tenant.Name)
			return nil
		}
	},
}

func init() {
	cmd.TenantCmd().AddCommand(createCmd)
	createCmd.Flags().StringVar(&createID, "id", "", "Tenant ID (required)")
	createCmd.Flags().StringVar(&createName, "name", "", "Tenant name (required)")
	createCmd.Flags().StringVar(&createQuotaFile, "quota-file", "", "Path to JSON file containing quota settings")
	createCmd.Flags().StringVar(&createMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
	createCmd.MarkFlagRequired("id")
	createCmd.MarkFlagRequired("name")
}




