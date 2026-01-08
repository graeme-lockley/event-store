package apikey

import (
	"fmt"
	"strings"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	createUserID    string
	createName      string
	createTenant    string
	createDescription string
	createExpiresAt string
	createScopes    string
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new API key",
	Long:  `Create a new API key for a user. The key will be displayed only once.`,
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

		if createUserID == "" {
			return fmt.Errorf("user ID is required (use --user-id)")
		}
		if createName == "" {
			return fmt.Errorf("name is required (use --name)")
		}

		req := client.CreateAPIKeyRequest{
			Name: createName,
		}

		if createDescription != "" {
			req.Description = createDescription
		}

		if createExpiresAt != "" {
			req.ExpiresAt = createExpiresAt
		}

		if createScopes != "" {
			req.Scopes = strings.Split(createScopes, ",")
			// Trim whitespace from each scope
			for i, scope := range req.Scopes {
				req.Scopes[i] = strings.TrimSpace(scope)
			}
		}

		// Create API key
		apiKey, err := apiClient.CreateAPIKey(tenantID, createUserID, req)
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

		// For JSON/CSV output, return the full object
		switch cfg.Output.Format {
		case "json":
			return output.PrintAPIKeyJSON(apiKey)
		case "csv":
			return output.PrintAPIKeyCSV(apiKey)
		default:
			// For table format, display key prominently
			output.PrintMessage("API key created successfully")
			fmt.Printf("\n")
			fmt.Printf("IMPORTANT: Save this API key now. It will not be shown again!\n")
			fmt.Printf("\n")
			fmt.Printf("API Key: %s\n", apiKey.Key)
			fmt.Printf("\n")
			fmt.Printf("ID: %s\n", apiKey.ID)
			fmt.Printf("Name: %s\n", apiKey.Name)
			if apiKey.Description != "" {
				fmt.Printf("Description: %s\n", apiKey.Description)
			}
			return nil
		}
	},
}

func init() {
	cmd.APIKeyCmd().AddCommand(createCmd)
	createCmd.Flags().StringVar(&createUserID, "user-id", "", "User ID (required)")
	createCmd.Flags().StringVar(&createName, "name", "", "API key name (required)")
	createCmd.Flags().StringVar(&createTenant, "tenant", "", "Tenant ID (or use default)")
	createCmd.Flags().StringVar(&createDescription, "description", "", "API key description")
	createCmd.Flags().StringVar(&createExpiresAt, "expires-at", "", "Expiration date (ISO 8601 format)")
	createCmd.Flags().StringVar(&createScopes, "scopes", "", "Comma-separated list of scopes")
	createCmd.MarkFlagRequired("user-id")
	createCmd.MarkFlagRequired("name")
}




