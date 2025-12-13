package cmd

import (
	"github.com/spf13/cobra"
)

// topicCmd represents the topic command
var topicCmd = &cobra.Command{
	Use:   "topic",
	Short: "Manage topics",
	Long:  `Manage topics in the event store. Topics are used to organize and categorize events.`,
}

// TopicCmd returns the topic command for use in subcommands
func TopicCmd() *cobra.Command {
	return topicCmd
}

func init() {
	rootCmd.AddCommand(topicCmd)
}
