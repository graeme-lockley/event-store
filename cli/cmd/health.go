package cmd

import (
	"github.com/spf13/cobra"
)

// healthCmd represents the health command
var healthCmd = &cobra.Command{
	Use:   "health",
	Short: "Check event store health status",
	Long:  `Check the health status of the event store, including status, consumer count, and running dispatchers.`,
}

// HealthCmd returns the health command for use in subcommands
func HealthCmd() *cobra.Command {
	return healthCmd
}

func init() {
	rootCmd.AddCommand(healthCmd)
}
