// Event Store Client Library for Admin UI
// Provides a clean, type-safe interface for interacting with the Event Store API

export interface EventStoreConfig {
  baseUrl: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
}

export interface EventStoreError extends Error {
  status?: number;
}

export interface Event {
  id: string; // Generated as <topic>-<sequence>
  timestamp: string; // ISO8601
  type: string; // e.g. "user.created"
  payload: Record<string, unknown>; // Valid JSON object payload
}

export interface EventRequest {
  topic: string;
  type: string;
  payload: unknown;
}

export interface EventsQuery {
  sinceEventId?: string;
  date?: string;
  limit?: number;
}

export interface Schema {
  eventType: string;
  type: string;
  $schema: string;
  properties: Record<string, unknown>;
  required: string[];
  [key: string]: unknown;
}

export interface Topic {
  name: string;
  sequence: number;
  schemas: Schema[];
}

export interface Consumer {
  id: string; // UUID
  callback: string;
  topics: Record<string, string | null>; // topic -> lastEventId (or null)
}

export interface ConsumerRegistration {
  callback: string;
  topics: Record<string, string | null>; // topic -> lastEventId (or null)
}

export interface HealthStatus {
  status: string;
  consumers: number;
  runningDispatchers: string[];
}

export class EventStoreClient {
  private config: Required<EventStoreConfig>;

  constructor(config: EventStoreConfig) {
    const envBaseUrl: string | undefined = Deno.env.get("EVENT_STORE_BASE_URL");

    const rawBaseUrl = config?.baseUrl ?? envBaseUrl;
    if (!rawBaseUrl || typeof rawBaseUrl !== "string") {
      throw new Error(
        "EventStoreClient: baseUrl is required. Provide config.baseUrl or set EVENT_STORE_BASE_URL.",
      );
    }

    this.config = {
      baseUrl: rawBaseUrl.replace(/\/$/, ""), // Remove trailing slash
      timeout: config.timeout ?? 30000,
      retries: config.retries ?? 3,
      retryDelay: config.retryDelay ?? 1000,
    };
  }

  // Core request method with retry logic
  protected async request<T>(
    endpoint: string,
    options?: RequestInit,
    timeoutMs?: number,
  ): Promise<T> {
    const url = `${this.config.baseUrl}${endpoint}`;
    const timeout = timeoutMs ?? this.config.timeout;

    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= this.config.retries; attempt++) {
      try {
        // Create an AbortController for timeout
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        try {
          const response = await fetch(url, {
            headers: {
              "Content-Type": "application/json",
              ...options?.headers,
            },
            signal: controller.signal,
            ...options,
          });

          if (!response.ok) {
            const errorText = await response.text();
            const error: EventStoreError = new Error(
              `HTTP ${response.status}: ${response.statusText} - ${errorText}`,
            );
            error.status = response.status;
            throw error;
          }

          return await response.json();
        } catch (error) {
          if (error instanceof Error && error.name === "AbortError") {
            throw new Error(`Request timeout after ${timeout}ms`);
          }
          throw error;
        } finally {
          clearTimeout(timeoutId);
        }
      } catch (error) {
        lastError = error as Error;

        // Don't retry on client errors (4xx)
        if (
          error instanceof Error && "status" in error &&
          (error as EventStoreError).status &&
          (error as EventStoreError).status! < 500
        ) {
          throw error;
        }

        // If this is the last attempt, throw the error
        if (attempt === this.config.retries) {
          throw error;
        }

        // Wait before retrying
        await new Promise((resolve) =>
          setTimeout(resolve, this.config.retryDelay * Math.pow(2, attempt))
        );
      }
    }

    throw lastError || new Error("Request failed after all retries");
  }

  // Health check
  async getHealth(): Promise<HealthStatus> {
    return this.request<HealthStatus>("/health");
  }

  // Topic management
  async createTopic(name: string, schemas: Schema[]): Promise<void> {
    await this.request("/topics", {
      method: "POST",
      body: JSON.stringify({ name, schemas }),
    });
  }

  async getTopics(): Promise<Topic[]> {
    const response = await this.request<{ topics: Topic[] }>("/topics");
    return response.topics;
  }

  async getTopic(name: string): Promise<Topic> {
    return this.request<Topic>(`/topics/${encodeURIComponent(name)}`);
  }

  async updateTopicSchemas(name: string, schemas: Schema[]): Promise<void> {
    await this.request(`/topics/${encodeURIComponent(name)}`, {
      method: "PUT",
      body: JSON.stringify({ schemas }),
    });
  }

  // Event management
  async publishEvents(events: EventRequest[]): Promise<string[]> {
    if (!Array.isArray(events) || events.length === 0) {
      throw new Error("Events must be a non-empty array");
    }

    const response = await this.request<{ eventIds: string[] }>("/events", {
      method: "POST",
      body: JSON.stringify(events),
    });
    return response.eventIds;
  }

  async publishEvent(
    topic: string,
    type: string,
    payload: unknown,
  ): Promise<string> {
    const eventIds = await this.publishEvents([{ topic, type, payload }]);
    return eventIds[0];
  }

  async getEvents(topic: string, query?: EventsQuery): Promise<Event[]> {
    const params = new URLSearchParams();
    if (query?.sinceEventId) params.append("sinceEventId", query.sinceEventId);
    if (query?.date) params.append("date", query.date);
    if (query?.limit) params.append("limit", query.limit.toString());

    const endpoint = `/topics/${encodeURIComponent(topic)}/events${
      params.toString() ? `?${params.toString()}` : ""
    }`;

    const response = await this.request<{ events: Event[] }>(endpoint);
    return response.events;
  }

  // Consumer management
  async registerConsumer(registration: ConsumerRegistration): Promise<string> {
    const response = await this.request<{ consumerId: string }>(
      "/consumers/register",
      {
        method: "POST",
        body: JSON.stringify(registration),
      },
    );
    return response.consumerId;
  }

  async getConsumers(): Promise<Consumer[]> {
    const response = await this.request<{ consumers: Consumer[] }>(
      "/consumers",
    );
    return response.consumers;
  }

  async unregisterConsumer(id: string): Promise<void> {
    await this.request(`/consumers/${encodeURIComponent(id)}`, {
      method: "DELETE",
    });
  }

  // Utility methods
  async testConnection(): Promise<boolean> {
    try {
      await this.getHealth();
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Wait for the Event Store server to be ready by polling the health endpoint.
   * This is useful for tests or scenarios where you need to ensure the server is fully started.
   *
   * @param options - Configuration options for the wait behavior
   * @param options.maxWaitTime - Maximum time to wait in milliseconds (default: 5000)
   * @param options.pollInterval - Interval between health checks in milliseconds (default: 100)
   * @param options.throwOnTimeout - Whether to throw an error on timeout (default: true)
   * @returns Promise that resolves when server is ready, or throws if timeout and throwOnTimeout is true
   */
  async waitForServer(options?: {
    maxWaitTime?: number;
    pollInterval?: number;
    throwOnTimeout?: boolean;
  }): Promise<void> {
    const maxWaitTime = options?.maxWaitTime ?? 5000;
    const pollInterval = options?.pollInterval ?? 100;
    const throwOnTimeout = options?.throwOnTimeout ?? true;

    const startTime = Date.now();

    while (Date.now() - startTime < maxWaitTime) {
      try {
        await this.getHealth();
        // If we get here, server is ready
        return;
      } catch (_error) {
        // Server not ready yet, wait and try again
        await new Promise((resolve) => setTimeout(resolve, pollInterval));
      }
    }

    // Timeout reached
    if (throwOnTimeout) {
      throw new Error(`Server failed to start within ${maxWaitTime}ms`);
    }
  }

  // Batch operations
  async publishEventsBatch(
    events: EventRequest[],
    batchSize: number = 100,
  ): Promise<string[]> {
    const allEventIds: string[] = [];

    for (let i = 0; i < events.length; i += batchSize) {
      const batch = events.slice(i, i + batchSize);
      const eventIds = await this.publishEvents(batch);
      allEventIds.push(...eventIds);
    }

    return allEventIds;
  }

  // Event streaming (future enhancement)
  async *streamEvents(topic: string, options?: {
    sinceEventId?: string;
    pollInterval?: number;
    signal?: AbortSignal;
  }): AsyncGenerator<Event> {
    let lastEventId = options?.sinceEventId;
    const pollInterval = options?.pollInterval ?? 1000;

    while (true) {
      if (options?.signal?.aborted) {
        break;
      }
      try {
        const events = await this.getEvents(topic, {
          sinceEventId: lastEventId,
          limit: 100,
        });

        for (const event of events) {
          yield event;
          lastEventId = event.id;
        }

        // Wait before next poll
        await new Promise((resolve) => setTimeout(resolve, pollInterval));
      } catch (error) {
        console.error("Error streaming events:", error);
        // Wait before retrying
        await new Promise((resolve) => setTimeout(resolve, pollInterval * 2));
      }
    }
  }
}

