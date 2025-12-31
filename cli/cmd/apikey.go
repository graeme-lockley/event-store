package cmd

import (
	"github.com/spf13/cobra"
)

var apikeyCmd = &cobra.Command{
	Use:   "apikey",
	Short: "Manage API keys",
	Long:  `Manage API keys for users in the event store.`,
}

// APIKeyCmd returns the apikey command for use in subcommands
func APIKeyCmd() *cobra.Command {
	return apikeyCmd
}

func init() {
	rootCmd.AddCommand(apikeyCmd)
}



