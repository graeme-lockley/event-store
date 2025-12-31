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
	updateName         string
	updateQuotaFile    string
	updateMetadataFile string
)

var updateCmd = &cobra.Command{
	Use:   "update <tenantId>",
	Short: "Update a tenant",
	Long:  `Update tenant settings including name, quota, and metadata.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := args[0]
		req := client.TenantUpdateRequest{}

		// Update name if provided
		if updateName != "" {
			req.Name = updateName
		}

		// Load quota from file if provided
		if updateQuotaFile != "" {
			quotaData, err := os.ReadFile(updateQuotaFile)
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

		// Update tenant
		tenant, err := apiClient.UpdateTenant(tenantID, req)
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

		message := fmt.Sprintf("Tenant '%s' updated successfully", tenant.ID)
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
	cmd.TenantCmd().AddCommand(updateCmd)
	updateCmd.Flags().StringVar(&updateName, "name", "", "Tenant name")
	updateCmd.Flags().StringVar(&updateQuotaFile, "quota-file", "", "Path to JSON file containing quota settings")
	updateCmd.Flags().StringVar(&updateMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
}

