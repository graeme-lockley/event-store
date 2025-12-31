package context

import (
	"fmt"

	"github.com/event-store/cli/cmd"
	"github.com/spf13/cobra"
)

var showCmd = &cobra.Command{
	Use:   "show",
	Short: "Show current default tenant, namespace, username, and API key",
	Long:  `Show the currently configured default tenant, namespace, username, and API key.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		if cfg == nil {
			return fmt.Errorf("config not loaded")
		}

		server := cfg.Server
		fmt.Printf("Default tenant: %s\n", getValueOrEmpty(server.TenantID))
		fmt.Printf("Default namespace: %s\n", getValueOrEmpty(server.NamespaceID))
		fmt.Printf("Default username: %s\n", getValueOrEmpty(server.Username))
		if server.APIKey != "" {
			fmt.Printf("Default API key: %s\n", maskAPIKey(server.APIKey))
		} else {
			fmt.Printf("Default API key: %s\n", "(not set)")
		}

		return nil
	},
}

func init() {
	cmd.ContextCmd().AddCommand(showCmd)
}

