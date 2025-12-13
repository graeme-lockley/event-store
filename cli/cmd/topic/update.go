package topic

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/event-store/cli/cmd"
	"github.com/event-store/cli/internal/client"
	"github.com/event-store/cli/internal/output"
)

var updateSchemasFile string

var updateCmd = &cobra.Command{
	Use:   "update <name>",
	Short: "Update topic schemas",
	Long:  `Update schemas for an existing topic. Schema updates are additive only - you can add new schemas or update existing ones, but cannot remove schemas.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		topicName := args[0]

		if updateSchemasFile == "" {
			return fmt.Errorf("schemas file is required (use --schemas-file)")
		}

		// Read schemas from file
		schemaData, err := os.ReadFile(updateSchemasFile)
		if err != nil {
			return fmt.Errorf("failed to read schemas file: %w", err)
		}

		var schemas []client.Schema
		if err := json.Unmarshal(schemaData, &schemas); err != nil {
			return fmt.Errorf("failed to parse schemas JSON: %w", err)
		}

		// Validate schemas
		if len(schemas) == 0 {
			return fmt.Errorf("at least one schema is required")
		}

		// Update topic schemas
		if err := apiClient.UpdateTopicSchemas(topicName, schemas); err != nil {
			if cfg.Output.Format == "json" {
				return output.PrintErrorJSON(err)
			}
			if cfg.Output.Format == "csv" {
				return output.PrintErrorCSV(err)
			}
			output.PrintError(err)
			return err
		}

		message := fmt.Sprintf("Topic '%s' schemas updated successfully", topicName)
		switch cfg.Output.Format {
		case "json":
			return output.PrintMessageJSON(message)
		case "csv":
			return output.PrintMessageCSV(message)
		default:
			output.PrintMessage(message)
			return nil
		}
	},
}

func init() {
	cmd.TopicCmd().AddCommand(updateCmd)
	updateCmd.Flags().StringVar(&updateSchemasFile, "schemas-file", "", "Path to JSON file containing schemas array (required)")
	updateCmd.MarkFlagRequired("schemas-file")
}
