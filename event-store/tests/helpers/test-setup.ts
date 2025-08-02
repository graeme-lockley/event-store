import { ensureDir } from "../../deps.ts";

/**
 * Test setup helper to manage test directories and cleanup
 */
export class TestSetup {
  private testId: string;
  private dataDir: string;
  private configDir: string;

  constructor(testId?: string) {
    this.testId = testId || this.generateTestId();
    this.dataDir = `./data-test-${this.testId}`;
    this.configDir = `./config-test-${this.testId}`;
  }

  /**
   * Generate a unique test ID
   */
  private generateTestId(): string {
    return Math.random().toString(36).substring(2, 8);
  }

  /**
   * Set up test environment with dedicated directories
   */
  async setup(): Promise<void> {
    // Set environment variables for test directories
    Deno.env.set("DATA_DIR", this.dataDir);
    Deno.env.set("CONFIG_DIR", this.configDir);

    // Ensure test directories exist synchronously
    try {
      await ensureDir(this.dataDir);
      await ensureDir(this.configDir);
    } catch (error) {
      // If directories already exist, that's fine
      if (!(error instanceof Deno.errors.AlreadyExists)) {
        throw error;
      }
    }
  }

  /**
   * Clean up test directories
   */
  async cleanup(): Promise<void> {
    try {
      // Remove test data directory
      await Deno.remove(this.dataDir, { recursive: true });
    } catch (error) {
      // Ignore errors if directory doesn't exist
      if (!(error instanceof Deno.errors.NotFound)) {
        console.warn(
          `Warning: Could not remove test data directory ${this.dataDir}:`,
          error,
        );
      }
    }

    try {
      // Remove test config directory
      await Deno.remove(this.configDir, { recursive: true });
    } catch (error) {
      // Ignore errors if directory doesn't exist
      if (!(error instanceof Deno.errors.NotFound)) {
        console.warn(
          `Warning: Could not remove test config directory ${this.configDir}:`,
          error,
        );
      }
    }
  }

  /**
   * Get test data directory path
   */
  getDataDir(): string {
    return this.dataDir;
  }

  /**
   * Get test config directory path
   */
  getConfigDir(): string {
    return this.configDir;
  }

  /**
   * Get test ID
   */
  getTestId(): string {
    return this.testId;
  }
}

/**
 * Clean up all test directories (for use in afterAll hooks)
 */
export async function cleanupAllTestDirectories(): Promise<void> {
  try {
    // List all directories in current directory
    for await (const entry of Deno.readDir(".")) {
      if (entry.isDirectory) {
        // Check if it matches our test directory patterns
        if (
          entry.name.startsWith("data-test-") ||
          entry.name.startsWith("config-test-")
        ) {
          try {
            await Deno.remove(entry.name, { recursive: true });
            console.log(`Cleaned up test directory: ${entry.name}`);
          } catch (error) {
            console.warn(
              `Warning: Could not remove test directory ${entry.name}:`,
              error,
            );
          }
        }
      }
    }
  } catch (error) {
    console.warn("Warning: Could not list directories for cleanup:", error);
  }
}
