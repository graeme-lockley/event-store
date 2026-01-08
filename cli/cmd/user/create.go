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
	createEmail         string
	createName          string
	createPassword      string
	createTenant        string
	createPrimaryTenant string
	createMetadataFile  string
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new user",
	Long:  `Create a new user in a tenant.`,
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

		if createEmail == "" {
			return fmt.Errorf("email is required (use --email)")
		}
		if createName == "" {
			return fmt.Errorf("name is required (use --name)")
		}
		if createPassword == "" {
			return fmt.Errorf("password is required (use --password)")
		}

		req := client.UserCreateRequest{
			Email:  createEmail,
			Name:   createName,
			Password: createPassword,
		}

		if createPrimaryTenant != "" {
			req.PrimaryTenantID = createPrimaryTenant
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

		// Create user
		user, err := apiClient.CreateUser(tenantID, req)
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

		message := fmt.Sprintf("User '%s' created successfully", user.ID)
		switch cfg.Output.Format {
		case "json":
			return output.PrintUserJSON(user)
		case "csv":
			return output.PrintUserCSV(user)
		default:
			output.PrintMessage(message)
			fmt.Printf("ID: %s\n", user.ID)
			fmt.Printf("Email: %s\n", user.Email)
			fmt.Printf("Name: %s\n", user.Name)
			return nil
		}
	},
}

func init() {
	cmd.UserCmd().AddCommand(createCmd)
	createCmd.Flags().StringVar(&createEmail, "email", "", "User email (required)")
	createCmd.Flags().StringVar(&createName, "name", "", "User name (required)")
	createCmd.Flags().StringVar(&createPassword, "password", "", "User password (required)")
	createCmd.Flags().StringVar(&createTenant, "tenant", "", "Tenant ID (or use default)")
	createCmd.Flags().StringVar(&createPrimaryTenant, "primary-tenant-id", "", "Primary tenant ID")
	createCmd.Flags().StringVar(&createMetadataFile, "metadata-file", "", "Path to JSON file containing metadata")
	createCmd.MarkFlagRequired("email")
	createCmd.MarkFlagRequired("name")
	createCmd.MarkFlagRequired("password")
}




