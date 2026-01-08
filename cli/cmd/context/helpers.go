package context

// getValueOrEmpty returns the value or "(not set)" if empty
func getValueOrEmpty(val string) string {
	if val == "" {
		return "(not set)"
	}
	return val
}

// maskAPIKey masks an API key for display (shows first 4 and last 4 characters)
func maskAPIKey(key string) string {
	if len(key) <= 8 {
		return "****"
	}
	return key[:4] + "..." + key[len(key)-4:]
}




