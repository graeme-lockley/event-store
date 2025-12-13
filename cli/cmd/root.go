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

	// Bind flags to viper for config file support
	viper.BindPFlag("server.url", rootCmd.PersistentFlags().Lookup("server-url"))
	viper.BindPFlag("output.format", rootCmd.PersistentFlags().Lookup("output"))
}

// GetConfig returns the loaded configuration
func GetConfig() *config.Config {
	return cfg
}
