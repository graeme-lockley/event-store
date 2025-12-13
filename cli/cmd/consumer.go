package cmd

import (
	"github.com/spf13/cobra"
)

// consumerCmd represents the consumer command
var consumerCmd = &cobra.Command{
	Use:   "consumer",
	Short: "Manage consumers",
	Long:  `Manage consumers in the event store. Consumers receive events from topics via webhooks.`,
}

// ConsumerCmd returns the consumer command for use in subcommands
func ConsumerCmd() *cobra.Command {
	return consumerCmd
}

func init() {
	rootCmd.AddCommand(consumerCmd)
}
