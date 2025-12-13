package main

import (
	"github.com/event-store/cli/cmd"
	_ "github.com/event-store/cli/cmd/consumer" // Import to register consumer subcommands
	_ "github.com/event-store/cli/cmd/event"    // Import to register event subcommands
	_ "github.com/event-store/cli/cmd/health"   // Import to register health subcommands
	_ "github.com/event-store/cli/cmd/topic"    // Import to register topic subcommands
)

func main() {
	cmd.Execute()
}
