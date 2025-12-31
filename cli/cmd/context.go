package cmd

import (
	"github.com/spf13/cobra"
)

var contextCmd = &cobra.Command{
	Use:   "context",
	Short: "Manage default tenant, namespace, and username",
	Long:  `Manage the default tenant, namespace, and username that are used for all commands.`,
}

// ContextCmd returns the context command for use in subcommands
func ContextCmd() *cobra.Command {
	return contextCmd
}

func init() {
	rootCmd.AddCommand(contextCmd)
}



