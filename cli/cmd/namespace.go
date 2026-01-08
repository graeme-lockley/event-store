package cmd

import (
	"github.com/spf13/cobra"
)

var namespaceCmd = &cobra.Command{
	Use:   "namespace",
	Short: "Manage namespaces",
	Long:  `Manage namespaces in the event store.`,
}

// NamespaceCmd returns the namespace command for use in subcommands
func NamespaceCmd() *cobra.Command {
	return namespaceCmd
}

func init() {
	rootCmd.AddCommand(namespaceCmd)
}




