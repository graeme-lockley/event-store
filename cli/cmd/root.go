package cmd

import (
	"fmt"
	"os"

	"github.com/event-store/cli/internal/config"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	serverURL    string
	outputFormat string
	configPath   string
	tenantID     string
	namespaceID  string
	username     string
	apiKey       string
	cfg          *config.Config
)

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:   "es",
	Short: "Event Store CLI - Manage topics and consumers",
	Long: `Event Store CLI is a command-line tool for managing an event store instance.
It provides commands for managing topics and consumers with support for
table, JSON, and CSV output formats.`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Load configuration
		var err error
		cfg, err = config.LoadConfig(configPath)
		if err != nil {
			return fmt.Errorf("failed to load config: %w", err)
		}

		// Override with command-line flags if provided
		if serverURL != "" {
			cfg.Server.URL = serverURL
		}
		if outputFormat != "" {
			cfg.Output.Format = outputFormat
		}

		// Validate output format
		if cfg.Output.Format != "table" && cfg.Output.Format != "json" && cfg.Output.Format != "csv" {
			return fmt.Errorf("invalid output format: %s (must be 'table', 'json', or 'csv')", cfg.Output.Format)
		}

		return nil
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	// Global flags
	rootCmd.PersistentFlags().StringVarP(&serverURL, "server-url", "s", "", "Event store server URL (default: http://localhost:8000)")
	rootCmd.PersistentFlags().StringVarP(&outputFormat, "output", "o", "", "Output format: table, json, or csv (default: table)")
	rootCmd.PersistentFlags().StringVar(&configPath, "config", "", "Config file path (default: ~/.es/config.yaml)")
	rootCmd.PersistentFlags().StringVarP(&tenantID, "tenant", "t", "", "Tenant ID (overrides config default)")
	rootCmd.PersistentFlags().StringVarP(&namespaceID, "namespace", "n", "", "Namespace ID (overrides config default)")
	rootCmd.PersistentFlags().StringVarP(&username, "username", "u", "", "Username (overrides config default)")
	rootCmd.PersistentFlags().StringVar(&apiKey, "api-key", "", "API key (overrides config default)")

	// Bind flags to viper for config file support
	viper.BindPFlag("server.url", rootCmd.PersistentFlags().Lookup("server-url"))
	viper.BindPFlag("output.format", rootCmd.PersistentFlags().Lookup("output"))
}

// GetConfig returns the loaded configuration
func GetConfig() *config.Config {
	return cfg
}

// GetTenantID returns the tenant ID from flag or config default
func GetTenantID() string {
	if tenantID != "" {
		return tenantID
	}
	if cfg != nil && cfg.Server.TenantID != "" {
		return cfg.Server.TenantID
	}
	return ""
}

// GetNamespaceID returns the namespace ID from flag or config default
func GetNamespaceID() string {
	if namespaceID != "" {
		return namespaceID
	}
	if cfg != nil && cfg.Server.NamespaceID != "" {
		return cfg.Server.NamespaceID
	}
	return ""
}

// GetUsername returns the username from flag or config default
func GetUsername() string {
	if username != "" {
		return username
	}
	if cfg != nil && cfg.Server.Username != "" {
		return cfg.Server.Username
	}
	return ""
}

// GetAPIKey returns the API key from flag or config default
func GetAPIKey() string {
	if apiKey != "" {
		return apiKey
	}
	if cfg != nil && cfg.Server.APIKey != "" {
		return cfg.Server.APIKey
	}
	return ""
}

// SaveContext saves tenant, namespace, username, and API key to config file
func SaveContext(tenantID, namespaceID, username, apiKey string) error {
	if cfg == nil {
		return fmt.Errorf("config not loaded")
	}

	cfg.Server.TenantID = tenantID
	cfg.Server.NamespaceID = namespaceID
	cfg.Server.Username = username
	cfg.Server.APIKey = apiKey

	return config.SaveConfig(cfg, configPath)
}
