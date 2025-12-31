package user

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	assignUserID    string
	assignTenantID  string
	assignRole      string
	assignPrimary   bool
)

var assignCmd = &cobra.Command{
	Use:   "assign",
	Short: "Assign a user to a tenant",
	Long:  `Assign a user to a tenant with optional role and primary tenant setting.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL, cmd.GetAPIKey())

		tenantID := cmd.GetTenantID()
		if tenantID == "" {
			return fmt.Errorf("tenant ID is required (use --tenant flag or set default with 'es context set --tenant <id>')")
		}

		if assignUserID == "" {
			return fmt.Errorf("user ID is required (use --user-id)")
		}
		if assignTenantID == "" {
			return fmt.Errorf("target tenant ID is required (use --tenant-id)")
		}

		req := client.AssignUserTenantRequest{
			TenantID: assignTenantID,
		}

		if assignRole != "" {
			req.Role = assignRole
		}

		if assignPrimary {
			req.IsPrimary = true
		}

		err := apiClient.AssignUserToTenant(tenantID, assignUserID, req)
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

		message := fmt.Sprintf("User '%s' assigned to tenant '%s' successfully", assignUserID, assignTenantID)
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
	cmd.UserCmd().AddCommand(assignCmd)
	assignCmd.Flags().StringVar(&assignUserID, "user-id", "", "User ID (required)")
	assignCmd.Flags().StringVar(&assignTenantID, "tenant-id", "", "Target tenant ID (required)")
	assignCmd.Flags().StringVar(&assignRole, "role", "", "Role for user in tenant")
	assignCmd.Flags().BoolVar(&assignPrimary, "primary", false, "Set as primary tenant")
	assignCmd.MarkFlagRequired("user-id")
	assignCmd.MarkFlagRequired("tenant-id")
}



