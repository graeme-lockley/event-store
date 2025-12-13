package cmd

import (
	"github.com/spf13/cobra"
)

// eventCmd represents the event command
var eventCmd = &cobra.Command{
	Use:   "event",
	Short: "Manage events",
	Long:  `Manage and query events in the event store.`,
}

// EventCmd returns the event command for use in subcommands
func EventCmd() *cobra.Command {
	return eventCmd
}

func init() {
	rootCmd.AddCommand(eventCmd)
}

