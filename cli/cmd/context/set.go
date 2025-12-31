package context

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/spf13/cobra"
)

var (
	setTenantID    string
	setNamespaceID string
	setUsername    string
	setAPIKey      string
)

var setCmd = &cobra.Command{
	Use:   "set",
	Short: "Set default tenant, namespace, username, and API key",
	Long: `Set the default tenant, namespace, username, and API key that will be used for all commands
unless overridden by --tenant, --namespace, --username, or --api-key flags.

Examples:
  # Set all defaults
  es context set --tenant acme-corp --namespace billing-app --username john.doe --api-key es_xxx

  # Set only tenant and namespace
  es context set --tenant acme-corp --namespace billing-app

  # Set only username
  es context set --username john.doe`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		if cfg == nil {
			return fmt.Errorf("config not loaded")
		}

		// Get current values from config
		currentTenant := cfg.Server.TenantID
		currentNamespace := cfg.Server.NamespaceID
		currentUsername := cfg.Server.Username
		currentAPIKey := cfg.Server.APIKey

		// Update only if new values provided
		if setTenantID != "" {
			currentTenant = setTenantID
		}
		if setNamespaceID != "" {
			currentNamespace = setNamespaceID
		}
		if setUsername != "" {
			currentUsername = setUsername
		}
		if setAPIKey != "" {
			currentAPIKey = setAPIKey
		}

		// Save configuration
		if err := cmd.SaveContext(currentTenant, currentNamespace, currentUsername, currentAPIKey); err != nil {
			return fmt.Errorf("failed to save context config: %w", err)
		}

		fmt.Printf("Default tenant: %s\n", getValueOrEmpty(currentTenant))
		fmt.Printf("Default namespace: %s\n", getValueOrEmpty(currentNamespace))
		fmt.Printf("Default username: %s\n", getValueOrEmpty(currentUsername))
		if currentAPIKey != "" {
			fmt.Printf("Default API key: %s\n", maskAPIKey(currentAPIKey))
		} else {
			fmt.Printf("Default API key: %s\n", "(not set)")
		}

		return nil
	},
}

func init() {
	cmd.ContextCmd().AddCommand(setCmd)
	setCmd.Flags().StringVar(&setTenantID, "tenant", "", "Default tenant ID")
	setCmd.Flags().StringVar(&setNamespaceID, "namespace", "", "Default namespace ID")
	setCmd.Flags().StringVar(&setUsername, "username", "", "Default username")
	setCmd.Flags().StringVar(&setAPIKey, "api-key", "", "Default API key")
}

