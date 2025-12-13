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

var (
	createName       string
	createSchemasFile string
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new topic",
	Long:  `Create a new topic with schemas. Schemas define the structure of events for the topic.`,
	RunE: func(cobraCmd *cobra.Command, args []string) error {
		cfg := cmd.GetConfig()
		apiClient := client.NewClient(cfg.Server.URL)

		if createName == "" {
			return fmt.Errorf("topic name is required (use --name)")
		}

		if createSchemasFile == "" {
			return fmt.Errorf("schemas file is required (use --schemas-file)")
		}

		// Read schemas from file
		schemaData, err := os.ReadFile(createSchemasFile)
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

		// Create topic
		if err := apiClient.CreateTopic(createName, schemas); err != nil {
			if cfg.Output.Format == "json" {
				return output.PrintErrorJSON(err)
			}
			if cfg.Output.Format == "csv" {
				return output.PrintErrorCSV(err)
			}
			output.PrintError(err)
			return err
		}

		message := fmt.Sprintf("Topic '%s' created successfully", createName)
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
	cmd.TopicCmd().AddCommand(createCmd)
	createCmd.Flags().StringVar(&createName, "name", "", "Topic name (required)")
	createCmd.Flags().StringVar(&createSchemasFile, "schemas-file", "", "Path to JSON file containing schemas array (required)")
	createCmd.MarkFlagRequired("name")
	createCmd.MarkFlagRequired("schemas-file")
}
