package user

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
	updateEmail        string
	updateName         string
	updateTenant       string
	updateMetadataFile string
)

var updateCmd = &cobra.Command{
	Use:   "update <userId>",
	Short: "Update a user",
	Long:  `Update user settings including email, name, and metadata.`,
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

		userID := args[0]
		req := client.UserUpdateRequest{}

		if updateEmail != "" {
			req.Email = updateEmail
		}

		if updateName != "" {
			req.Name = updateName
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

		// Update user
		user, err := apiClient.UpdateUser(tenantID, userID, req)
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

		message := fmt.Sprintf("User '%s' updated successfully", user.ID)
		switch cfg.Output.Format {
		case "json":
			return output.PrintUserJSON(user)
		case "csv":
			return output.PrintUserCSV(user)
		default:
			output.PrintMessage(message)
			fmt.Printf("ID: %s\n", user.ID)
			fmt.Printf("Email: %s\n", user.Email)
			return nil
		}
	},
}

func init() {
	cmd.UserCmd().AddCommand(updateCmd)
	updateCmd.Flags().StringVar(&updateEmail, "email", "", "User email")
	updateCmd.Flags().StringVar(&updateName, "name", "", "User name")
	updateCmd.Flags().StringVar(&updateTenant, "tenant", "", "Tenant ID (or use default)")
	updateCmd.Flags().StringVar(&updateMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
}



