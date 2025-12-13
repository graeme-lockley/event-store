# Event Store CLI

A command-line tool for managing an event store instance. The CLI provides Azure CLI-style commands for managing topics and consumers with support for both human-readable table output and JSON output formats.

## Installation

### Build from Source

```bash
cd cli
go build -o es .
```

### Install via Go

```bash
go install github.com/event-store/cli@latest
```

## Configuration

The CLI supports configuration via a YAML file located at `~/.es/config.yaml`:

```yaml
server:
  url: http://localhost:8000
output:
  format: table  # table or json
```

You can also override these settings using command-line flags.

## Usage

### Global Flags

- `--server-url, -s`: Event store server URL (default: http://localhost:8000)
- `--output, -o`: Output format: `table`, `json`, or `csv` (default: `table`)
- `--config`: Config file path (default: ~/.es/config.yaml)

### Topic Commands

#### List Topics

```bash
es topic list
```

Lists all topics in the event store.

#### Show Topic Details

```bash
es topic show <name>
```

Shows detailed information about a specific topic, including its schemas.

#### Create Topic

```bash
es topic create --name <name> --schemas-file <file>
```

Creates a new topic with schemas from a JSON file.

The schemas file should contain a JSON array of schema objects:

```json
[
  {
    "eventType": "user.created",
    "type": "object",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "properties": {
      "id": { "type": "string" },
      "name": { "type": "string" },
      "email": { "type": "string" }
    },
    "required": ["id", "name", "email"]
  }
]
```

#### Update Topic Schemas

```bash
es topic update <name> --schemas-file <file>
```

Updates schemas for an existing topic. Schema updates are additive only - you can add new schemas or update existing ones, but cannot remove schemas.

### Event Commands

#### List Events

```bash
es event list <topic> [flags]
```

Lists events from a topic with optional filtering and pagination.

**Flags:**
- `--from-event-id <id>` - Get events after this event ID
- `--limit <n>` - Maximum number of events to return (0 = no limit)
- `--date <YYYY-MM-DD>` - Get events from a specific date
- `--filter <filter>` - Filter events (format: `field:value`)

**Filter Examples:**
- Filter by event type: `--filter "type:user.created"`
- Filter by payload field: `--filter "payload.email:alice@example.com"`
- Filter by nested payload: `--filter "payload.user.id:123"`

**Examples:**
```bash
# List all events from a topic
es event list user-events

# List events starting from a specific event ID
es event list user-events --from-event-id user-events-10

# List up to 50 events
es event list user-events --limit 50

# List events from a specific date
es event list user-events --date 2025-01-15

# Filter events by type
es event list user-events --filter "type:user.created"

# Filter events by payload field
es event list user-events --filter "payload.email:alice@example.com"
```

#### Show Event Details

```bash
es event show <topic> <event-id>
```

Shows detailed information about a specific event, including the full payload without truncation.

**Examples:**
```bash
# Show an event by ID
es event show user-events user-events-10

# Show an event in JSON format
es event show user-events user-events-10 --output json
```

### Consumer Commands

#### List Consumers

```bash
es consumer list
```

Lists all registered consumers in the event store.

#### Show Consumer Details

```bash
es consumer show <id>
```

Shows detailed information about a specific consumer, including its callback URL and subscribed topics.

#### Register Consumer

```bash
es consumer register --callback <url> --topics <topics>
```

Registers a new consumer that will receive events from specified topics via webhook.

The `--topics` flag accepts a comma-separated list of topic mappings in the format:
- `topic1:eventId1,topic2:eventId2` - Start from specific event IDs
- `topic1:null,topic2:null` - Receive all events from topics

Example:
```bash
es consumer register \
  --callback https://example.com/webhook \
  --topics "user-events:null,audit-events:audit-events-5"
```

#### Delete Consumer

```bash
es consumer delete <id>
```

Unregisters a consumer. The consumer will stop receiving events.

## Output Formats

### Table Format (Default)

The default output format is human-readable tables:

```
+-------------+----------+--------------+
| Name        | Sequence | Schema Count |
+-------------+----------+--------------+
| user-events | 42       | 2            |
+-------------+----------+--------------+
```

### JSON Format

Use the `--output json` flag for JSON output, useful for scripting:

### CSV Format

Use the `--output csv` flag for CSV output, useful for importing into spreadsheets or data analysis tools:

```bash
es topic list --output csv
```

Output:
```csv
Name,Sequence,Schema Count
user-events,42,2
audit-events,15,1
```

**Note:** CSV output is best suited for list commands. For detailed views, JSON or table format is recommended.

```bash
es topic list --output json
```

Output:
```json
{
  "topics": [
    {
      "name": "user-events",
      "sequence": 42,
      "schemas": [...]
    }
  ]
}
```

## Examples

### List all topics

```bash
es topic list
```

### List events with filtering

```bash
# List events from a topic
es event list user-events

# List events with limit and filter
es event list user-events --limit 10 --filter "type:user.created"
```

### Show event details

```bash
# Show a specific event with full payload
es event show user-events user-events-10
```

### Show topic details in JSON format

```bash
es topic show user-events --output json
```

### Create a topic from a schema file

```bash
es topic create --name user-events --schemas-file schemas.json
```

### Register a consumer for all events from a topic

```bash
es consumer register \
  --callback https://my-service.com/webhook \
  --topics "user-events:null"
```

### List consumers with custom server URL

```bash
es consumer list --server-url http://localhost:9000
```

## Error Handling

The CLI provides clear error messages for common issues:

- Invalid server URL or connection errors
- Missing required arguments
- Invalid JSON in schema files
- API errors with error codes

Exit codes:
- `0`: Success
- `1`: Error occurred

## Development

### Build

```bash
go build -o es .
```

### Run Tests

```bash
go test ./...
```

### Dependencies

- [cobra](https://github.com/spf13/cobra) - CLI framework
- [viper](https://github.com/spf13/viper) - Configuration management
- [go-pretty](https://github.com/jedib0t/go-pretty) - Table formatting
