package cmd

import (
	"github.com/spf13/cobra"
)

var tenantCmd = &cobra.Command{
	Use:   "tenant",
	Short: "Manage tenants",
	Long:  `Manage tenants in the event store.`,
}

// TenantCmd returns the tenant command for use in subcommands
func TenantCmd() *cobra.Command {
	return tenantCmd
}

func init() {
	rootCmd.AddCommand(tenantCmd)
}




