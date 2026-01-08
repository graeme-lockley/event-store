package cmd

import (
	"github.com/spf13/cobra"
)

var userCmd = &cobra.Command{
	Use:   "user",
	Short: "Manage users",
	Long:  `Manage users in the event store.`,
}

// UserCmd returns the user command for use in subcommands
func UserCmd() *cobra.Command {
	return userCmd
}

func init() {
	rootCmd.AddCommand(userCmd)
}




