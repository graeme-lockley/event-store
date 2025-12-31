package config

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/viper"
)

// Config represents the CLI configuration
type Config struct {
	Server ServerConfig `mapstructure:"server"`
	Output OutputConfig  `mapstructure:"output"`
}

// ServerConfig contains server connection settings
type ServerConfig struct {
	URL         string `mapstructure:"url"`
	TenantID    string `mapstructure:"tenantId,omitempty"`
	NamespaceID string `mapstructure:"namespaceId,omitempty"`
	Username    string `mapstructure:"username,omitempty"`
	APIKey      string `mapstructure:"apiKey,omitempty"`
}

// OutputConfig contains output format settings
type OutputConfig struct {
	Format string `mapstructure:"format"`
}

// DefaultConfig returns a configuration with default values
func DefaultConfig() *Config {
	return &Config{
		Server: ServerConfig{
			URL: "http://localhost:8000",
		},
		Output: OutputConfig{
			Format: "table",
		},
	}
}

// LoadConfig loads configuration from file or returns defaults
func LoadConfig(configPath string) (*Config, error) {
	cfg := DefaultConfig()

	if configPath == "" {
		// Use default path: ~/.es/config.yaml
		homeDir, err := os.UserHomeDir()
		if err != nil {
			return cfg, nil // Return defaults if we can't get home dir
		}
		configPath = filepath.Join(homeDir, ".es", "config.yaml")
	}

	// Check if config file exists
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return cfg, nil // Return defaults if file doesn't exist
	}

	viper.SetConfigFile(configPath)
	viper.SetConfigType("yaml")

	if err := viper.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	if err := viper.Unmarshal(cfg); err != nil {
		return nil, fmt.Errorf("failed to unmarshal config: %w", err)
	}

	return cfg, nil
}

// SaveConfig saves configuration to file
func SaveConfig(cfg *Config, configPath string) error {
	if configPath == "" {
		homeDir, err := os.UserHomeDir()
		if err != nil {
			return fmt.Errorf("failed to get home directory: %w", err)
		}
		configPath = filepath.Join(homeDir, ".es", "config.yaml")
	}

	// Create directory if it doesn't exist
	dir := filepath.Dir(configPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("failed to create config directory: %w", err)
	}

	viper.SetConfigFile(configPath)
	viper.SetConfigType("yaml")

	viper.Set("server.url", cfg.Server.URL)
	if cfg.Server.TenantID != "" {
		viper.Set("server.tenantId", cfg.Server.TenantID)
	}
	if cfg.Server.NamespaceID != "" {
		viper.Set("server.namespaceId", cfg.Server.NamespaceID)
	}
	if cfg.Server.Username != "" {
		viper.Set("server.username", cfg.Server.Username)
	}
	if cfg.Server.APIKey != "" {
		viper.Set("server.apiKey", cfg.Server.APIKey)
	}
	viper.Set("output.format", cfg.Output.Format)

	return viper.WriteConfig()
}
