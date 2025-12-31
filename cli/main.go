package main

import (
	"github.com/event-store/cli/cmd"
	_ "github.com/event-store/cli/cmd/apikey"   // Import to register apikey subcommands
	_ "github.com/event-store/cli/cmd/context"  // Import to register context subcommands
	_ "github.com/event-store/cli/cmd/consumer" // Import to register consumer subcommands
	_ "github.com/event-store/cli/cmd/event"    // Import to register event subcommands
	_ "github.com/event-store/cli/cmd/health"   // Import to register health subcommands
	_ "github.com/event-store/cli/cmd/namespace" // Import to register namespace subcommands
	_ "github.com/event-store/cli/cmd/tenant"    // Import to register tenant subcommands
	_ "github.com/event-store/cli/cmd/topic"     // Import to register topic subcommands
	_ "github.com/event-store/cli/cmd/user"      // Import to register user subcommands
)

func main() {
	cmd.Execute()
}
