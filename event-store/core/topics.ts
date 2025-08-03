import { ensureDir, join } from "../deps.ts";
import { Schema, TopicConfig } from "../types.ts";
import { SchemaValidator } from "../utils/validate.ts";

export class TopicManager {
  private configDir: string;
  private dataDir: string;
  private validator = new SchemaValidator();
  private initialized = false;

  private constructor() {
    this.configDir = Deno.env.get("CONFIG_DIR") || "config";
    this.dataDir = Deno.env.get("DATA_DIR") || "data";
  }

  static async create(): Promise<TopicManager> {
    const instance = new TopicManager();
    await instance.initializeAsync();
    return instance;
  }

  private async initializeAsync(): Promise<void> {
    await this.ensureDirectories();
    await this.loadExistingSchemas();
    this.initialized = true;
  }

  private async ensureDirectories(): Promise<void> {
    try {
      await ensureDir(this.configDir);
      await ensureDir(this.dataDir);
    } catch (error) {
      // If directories can't be created (e.g., during tests), that's okay
      // They will be created when needed
      console.warn(
        `Warning: Could not create directories ${this.configDir} or ${this.dataDir}:`,
        error,
      );
    }
  }

  /**
   * Ensure the TopicManager is initialized before use
   */
  private async ensureInitialized(): Promise<void> {
    if (!this.initialized) {
      await this.initializeAsync();
    }
  }

  /**
   * Create a new topic with schemas
   */
  async createTopic(name: string, schemas: Schema[]): Promise<void> {
    await this.ensureInitialized();
    const configPath = join(this.configDir, `${name}.json`);

    // Check if topic already exists
    try {
      await Deno.stat(configPath);
      throw new Error(`Topic '${name}' already exists`);
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) {
        // Topic doesn't exist, proceed with creation
      } else {
        throw error;
      }
    }

    // Validate schemas have required fields
    schemas.forEach((schema, index) => {
      if (!schema.eventType) {
        throw new Error(
          `Schema at index ${index} missing required 'eventType' field`,
        );
      }
      if (!schema.$schema) {
        throw new Error(
          `Schema at index ${index} missing required '$schema' field`,
        );
      }
    });

    const config: TopicConfig = {
      name,
      sequence: 0,
      schemas,
    };

    // Write config file
    await Deno.writeTextFile(configPath, JSON.stringify(config, null, 2));

    // Create topic data directory
    await ensureDir(join(this.dataDir, name));

    // Register schemas with validator
    this.validator.registerSchemas(name, schemas);

    console.log(`Created topic '${name}' with ${schemas.length} schemas`);
  }

  /**
   * Load topic configuration
   */
  async loadTopicConfig(name: string): Promise<TopicConfig> {
    await this.ensureInitialized();
    const configPath = join(this.configDir, `${name}.json`);

    try {
      const content = await Deno.readTextFile(configPath);
      const config: TopicConfig = JSON.parse(content);

      // Register schemas with validator if not already done
      this.validator.registerSchemas(name, config.schemas);

      return config;
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) {
        throw new Error(`Topic '${name}' not found`);
      }
      throw error;
    }
  }

  /**
   * Update topic sequence number
   */
  async updateSequence(name: string, sequence: number): Promise<void> {
    await this.ensureInitialized();
    const configPath = join(this.configDir, `${name}.json`);
    const config = await this.loadTopicConfig(name);
    config.sequence = sequence;

    await Deno.writeTextFile(configPath, JSON.stringify(config, null, 2));
  }

  /**
   * Get next event ID for a topic
   */
  async getNextEventId(name: string): Promise<string> {
    await this.ensureInitialized();
    const config = await this.loadTopicConfig(name);
    const nextSequence = config.sequence + 1;

    // Update the sequence
    await this.updateSequence(name, nextSequence);

    return `${name}-${nextSequence}`;
  }

  /**
   * Check if topic exists
   */
  async topicExists(name: string): Promise<boolean> {
    await this.ensureInitialized();
    const configPath = join(this.configDir, `${name}.json`);
    try {
      await Deno.stat(configPath);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Validate event against topic schemas
   */
  validateEvent(topic: string, eventType: string, payload: any): boolean {
    return this.validator.validateEvent(topic, eventType, payload);
  }

  /**
   * Load existing schemas from config files
   */
  private async loadExistingSchemas(): Promise<void> {
    try {
      const topics: string[] = [];

      // Check if config directory exists before trying to read it
      try {
        await Deno.stat(this.configDir);
      } catch {
        // Config directory doesn't exist yet, which is fine
        return;
      }

      for await (const entry of Deno.readDir(this.configDir)) {
        if (entry.isFile && entry.name.endsWith(".json")) {
          topics.push(entry.name.replace(".json", ""));
        }
      }

      for (const topic of topics) {
        try {
          const configPath = join(this.configDir, `${topic}.json`);
          const content = await Deno.readTextFile(configPath);
          const config: TopicConfig = JSON.parse(content);
          this.validator.registerSchemas(topic, config.schemas);
        } catch (error) {
          console.error(`Error loading schema for topic '${topic}':`, error);
        }
      }
    } catch (error) {
      console.error("Error loading existing schemas:", error);
    }
  }

  /**
   * Get all topic names
   */
  async getAllTopics(): Promise<string[]> {
    await this.ensureInitialized();
    const topics: string[] = [];

    for await (const entry of Deno.readDir(this.configDir)) {
      if (entry.isFile && entry.name.endsWith(".json")) {
        topics.push(entry.name.replace(".json", ""));
      }
    }

    return topics;
  }
}
