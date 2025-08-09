// Core type definitions for the Event Store
// This file contains all shared types used by both the implementation and client library

// ============================================================================
// CORE ENTITY TYPES
// ============================================================================

export type JSONValue =
  | string
  | number
  | boolean
  | null
  | JSONValue[]
  | JSONObject;
export type JSONObject = { [key: string]: JSONValue };

export interface Event {
  id: string; // Generated as <topic>-<sequence>
  timestamp: string; // ISO8601
  type: string; // e.g. "user.created"
  payload: JSONObject; // Valid JSON object payload
}

export interface EventRequest {
  topic: string;
  type: string;
  payload: unknown;
}

export interface Schema {
  eventType: string;
  type: string;
  $schema: string;
  properties: Record<string, unknown>;
  required: string[];
  [key: string]: any;
}

export interface Consumer {
  id: string; // UUID
  callback: string; // URL
  topics: Record<string, string | null>; // topic → lastEventId
  nudge(): Promise<void>; // Triggered when a new event is published
}

export interface ConsumerRegistration {
  callback: string;
  topics: Record<string, string | null>;
}

export interface TopicCreation {
  name: string;
  schemas: Schema[];
}

export interface EventsQuery {
  sinceEventId?: string;
  date?: string;
  limit?: number;
}

// ============================================================================
// INTERNAL IMPLEMENTATION TYPES
// ============================================================================

export interface TopicConfig {
  name: string;
  sequence: number;
  schemas: Schema[];
}

export interface EventResponse {
  eventIds: string[];
}

// ============================================================================
// CLIENT LIBRARY TYPES
// ============================================================================

export interface EventStoreConfig {
  baseUrl: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
}

export interface Topic {
  name: string;
  sequence: number;
  schemas: Schema[];
}

export interface HealthStatus {
  status: string;
  consumers: number;
  runningDispatchers: string[];
}

export interface EventStoreError extends Error {
  status?: number;
  code?: string;
}
