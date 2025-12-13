# Event Store - Feature Roadmap

This document outlines the features to be built following the architecture review. Features are organized by category and priority.

## Feature Categories

- **Critical**: Must-have for production readiness
- **High Priority**: Important for operational excellence
- **Medium Priority**: Valuable enhancements
- **Low Priority**: Nice-to-have features

---

## Critical Features

### CRIT-001: Consumer Persistence
**Category**: Critical  
**Priority**: P0  
**Description**: Implement persistent storage for consumer registrations. Currently consumers are stored in-memory and lost on restart. This prevents production deployment.

**Requirements**:
- Persist consumers to file system (similar to topics)
- Load consumers on application startup
- Preserve consumer state (lastEventId per topic)
- Resume dispatchers for existing consumers on startup
- Handle consumer state recovery after restart

**Estimated Effort**: 2-3 days

---

### CRIT-002: Tenant & Namespace Management
**Category**: Critical  
**Priority**: P0  
**Description**: Implement tenant and namespace hierarchy using event-sourced approach. This is foundational for multi-tenancy and security.

**Requirements**:
- Event-sourced tenant management in `$system/$management` namespace
- Event-sourced namespace management
- Tenant and namespace lifecycle operations (create, read, update, delete)
- Topic scoping to tenant/namespace
- Consumer scoping to tenant/namespace
- URL path structure: `/tenants/{tenantId}/namespaces/{namespaceId}/...`

**Estimated Effort**: 5-7 days

---

### CRIT-003: User Management
**Category**: Critical  
**Priority**: P0  
**Description**: System-wide user management with tenant associations. Users exist globally but are managed through tenant lens.

**Requirements**:
- System-wide user accounts (global identity)
- User authentication (login/logout)
- Password management (change, reset)
- User-to-tenant associations
- User profile management
- Session management
- User lifecycle operations (create, read, update, delete, suspend, activate)

**Estimated Effort**: 6-8 days

---

### CRIT-004: Permission & Authorization System
**Category**: Critical  
**Priority**: P0  
**Description**: Event-sourced permission system for unified security model across tenants, namespaces, topics, events, and consumers.

**Requirements**:
- Event-sourced permissions in `$system/$management/permissions` topic
- Permission model for all resources (tenant, namespace, topic, event, consumer, user)
- Permission projection service with caching
- Authorization middleware for API requests
- Permission grant/revoke operations
- Effective permission calculation (including roles/groups)
- Fine-grained constraints (event types, time-based, rate limits)

**Estimated Effort**: 7-10 days

---

### CRIT-005: API Key Management
**Category**: Critical  
**Priority**: P0  
**Description**: API key creation and management for programmatic access. API keys are associated with users and can have scoped permissions.

**Requirements**:
- Create API keys for users
- API key authentication
- API key lifecycle (create, read, revoke)
- API key scoping (tenant, namespace, topic)
- API key expiration
- API key usage tracking

**Estimated Effort**: 4-5 days

---

## High Priority Features

### HIGH-001: Structured Logging & Observability
**Category**: High Priority  
**Priority**: P1  
**Description**: Comprehensive logging and metrics for operational visibility. Currently logging is minimal and no metrics exist.

**Requirements**:
- Structured JSON logging for all business events
- Logging for: event publishing, consumer registration, delivery attempts, permission changes
- Metrics endpoint (Prometheus format)
- Key metrics: events published, delivery success/failure rates, consumer lag, dispatcher health
- Request/response logging with correlation IDs
- Error logging with stack traces

**Estimated Effort**: 3-4 days

---

### HIGH-002: Dead Letter Queue (DLQ)
**Category**: High Priority  
**Priority**: P1  
**Description**: Store failed event deliveries for manual review and retry. Currently failed deliveries result in consumer removal with no audit trail.

**Requirements**:
- DLQ topic per consumer or per tenant
- Store failed delivery attempts with error details
- Query DLQ entries
- Manual retry capability
- DLQ entry lifecycle (retry, delete, archive)
- Configurable retry policies
- DLQ size limits and cleanup

**Estimated Effort**: 4-5 days

---

### HIGH-003: Consumer State Recovery
**Category**: High Priority  
**Priority**: P1  
**Description**: Recover consumer positions and resume delivery after application restart or consumer re-registration.

**Requirements**:
- Load consumer state on startup
- Resume dispatchers for existing consumers
- Handle gaps in event delivery
- Consumer position recovery
- Backfill missed events
- Consumer health monitoring

**Estimated Effort**: 2-3 days

---

### HIGH-004: Role & Group Management
**Category**: High Priority  
**Priority**: P1  
**Description**: Predefined roles and groups for simplified permission management. Roles have predefined permission sets.

**Requirements**:
- Role creation and management
- Predefined roles (admin, developer, viewer, etc.)
- Group creation and management
- Assign users to roles
- Assign users to groups
- Role/group permission inheritance
- Tenant-scoped and system-scoped roles

**Estimated Effort**: 4-5 days

---

### HIGH-005: Event Replay
**Category**: High Priority  
**Priority**: P1  
**Description**: Replay events from a point in time or event ID. Useful for recovery, testing, and data migration.

**Requirements**:
- Replay events from specific event ID
- Replay events from timestamp
- Replay events for specific consumers
- Replay events with filters (event types)
- Replay to different consumers
- Replay rate limiting
- Replay progress tracking

**Estimated Effort**: 4-5 days

---

### HIGH-006: Consumer Correlation ID
**Category**: High Priority  
**Priority**: P1  
**Description**: Add correlation ID support for event delivery to consumers. Correlation IDs enable distributed tracing and help correlate webhook deliveries with consumer processing.

**Requirements**:
- Optional correlation ID field in consumer registration
- Store correlation ID with consumer configuration
- Auto-generate correlation ID (UUID v4) if not provided during registration
- Include correlation ID in HTTP header when delivering events to consumers
- Use standard header name: `X-Correlation-ID`
- Correlation ID included in consumer API responses (list/get consumer details)
- Correlation ID can be updated via consumer update operation
- Correlation IDs need not be unique (multiple consumers can share the same correlation ID)

**Implementation Notes**:
- Update `Consumer` domain entity to include `correlationId` field
- Update consumer registration DTO to accept optional `correlationId`
- Update `HttpConsumerDeliveryService` to include correlation ID in POST headers
- Correlation ID should be visible in logs for debugging
- If correlation ID is not provided during registration, generate UUID v4 automatically
- No uniqueness validation required for correlation IDs

**Estimated Effort**: 1-2 days

---

## Medium Priority Features

### MED-001: Consumer Management Enhancements
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Enhanced consumer management capabilities including pause/resume, health monitoring, and subscription updates.

**Requirements**:
- Pause/resume consumers
- Update consumer subscriptions (add/remove topics)
- Consumer health monitoring
- Consumer delivery statistics
- Consumer lag reporting
- Consumer configuration updates

**Estimated Effort**: 3-4 days

---

### MED-002: Event Filtering & Transformation
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Allow consumers to filter and transform events before delivery. Enables consumers to receive only relevant events.

**Requirements**:
- Consumer-side event filters
- Event transformation before delivery
- Conditional delivery based on event payload
- Filter by event type
- Filter by event payload fields
- Transformation rules (field mapping, enrichment)

**Estimated Effort**: 5-6 days

---

### MED-003: Event Retention Policies
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Automatic cleanup of old events based on time or size. Prevents unbounded storage growth.

**Requirements**:
- Time-based retention (e.g., delete events older than 90 days)
- Size-based retention (e.g., keep last N events)
- Per-topic retention policies
- Per-tenant retention policies
- Retention policy configuration
- Automatic cleanup jobs
- Retention policy exceptions (never delete certain events)

**Estimated Effort**: 3-4 days

---

### MED-004: Event Export
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Bulk export of events for backup, migration, or analysis. Support multiple formats and filtering.

**Requirements**:
- Export events by topic
- Export events by date range
- Export events by event type
- Multiple export formats (JSON, CSV, Parquet)
- Streaming export for large datasets
- Export progress tracking
- Export scheduling

**Estimated Effort**: 4-5 days

---

### MED-005: Audit Logging
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Comprehensive audit trail for all administrative operations. Critical for compliance and security.

**Requirements**:
- Audit log for all permission changes
- Audit log for user management operations
- Audit log for tenant/namespace/topic operations
- Audit log querying and filtering
- Audit log retention policies
- Audit log export
- User activity tracking

**Estimated Effort**: 3-4 days

---

### MED-006: Health & Monitoring Endpoints
**Category**: Medium Priority  
**Priority**: P2  
**Description**: Enhanced health checks and monitoring endpoints for operational visibility.

**Requirements**:
- Detailed health endpoint (beyond current basic health)
- Readiness probe
- Liveness probe
- Metrics endpoint (Prometheus)
- Dispatcher status endpoint
- Consumer status endpoint
- Storage usage metrics
- Performance metrics

**Estimated Effort**: 2-3 days

---

## Low Priority Features

### LOW-001: Consumer Groups
**Category**: Low Priority  
**Priority**: P3  
**Description**: Multiple consumers per topic with load balancing. Enables horizontal scaling of consumers.

**Requirements**:
- Consumer group concept
- Load balancing across consumers in group
- Consumer group coordination
- Partitioning strategies
- Consumer group rebalancing
- Consumer group management

**Estimated Effort**: 6-8 days

---

### LOW-002: Event Versioning
**Category**: Low Priority  
**Priority**: P3  
**Description**: Support multiple versions of event schemas. Enables schema evolution without breaking consumers.

**Requirements**:
- Schema versioning
- Multiple schema versions per event type
- Version compatibility checking
- Consumer version requirements
- Schema migration tools
- Version deprecation

**Estimated Effort**: 5-6 days

---

### LOW-003: Multi-Region Support
**Category**: Low Priority  
**Priority**: P3  
**Description**: Support for multi-region deployment with replication. Enables global distribution and disaster recovery.

**Requirements**:
- Region configuration
- Event replication between regions
- Region-aware routing
- Cross-region failover
- Region-specific storage
- Replication lag monitoring

**Estimated Effort**: 8-10 days

---

### LOW-004: Advanced Querying
**Category**: Low Priority  
**Priority**: P3  
**Description**: Advanced event querying capabilities beyond current basic filtering.

**Requirements**:
- Query by event payload fields
- Complex filtering expressions
- Aggregation queries
- Time-series queries
- Full-text search
- Query result pagination
- Query performance optimization

**Estimated Effort**: 6-8 days

---

### LOW-005: Webhook Delivery Enhancements
**Category**: Low Priority  
**Priority**: P3  
**Description**: Enhanced webhook delivery with retry strategies, signing, and custom headers.

**Requirements**:
- Configurable retry strategies per consumer
- Webhook signing (HMAC)
- Custom headers per consumer
- Delivery timeout configuration
- Delivery priority queues
- Delivery batching

**Estimated Effort**: 4-5 days

---

## Feature Dependencies

```
CRIT-002 (Tenant/Namespace) ──┐
                              ├──> CRIT-003 (User Management)
CRIT-004 (Permissions) ──────┘
                              │
                              ├──> CRIT-005 (API Keys)
                              │
                              └──> HIGH-004 (Roles/Groups)

CRIT-001 (Consumer Persistence) ──> HIGH-003 (Consumer Recovery)

HIGH-001 (Observability) ──> MED-006 (Health/Monitoring)

HIGH-002 (DLQ) ──> MED-001 (Consumer Management)
```

## Implementation Phases

### Phase 1: Foundation (Critical)
- CRIT-001: Consumer Persistence
- CRIT-002: Tenant & Namespace Management
- CRIT-003: User Management
- CRIT-004: Permission & Authorization System
- CRIT-005: API Key Management

**Timeline**: 6-8 weeks

### Phase 2: Operations (High Priority)
- HIGH-001: Structured Logging & Observability
- HIGH-002: Dead Letter Queue
- HIGH-003: Consumer State Recovery
- HIGH-004: Role & Group Management
- HIGH-005: Event Replay
- HIGH-006: Consumer Correlation ID

**Timeline**: 4-5 weeks

### Phase 3: Enhancements (Medium Priority)
- MED-001: Consumer Management Enhancements
- MED-002: Event Filtering & Transformation
- MED-003: Event Retention Policies
- MED-004: Event Export
- MED-005: Audit Logging
- MED-006: Health & Monitoring Endpoints

**Timeline**: 6-8 weeks

### Phase 4: Advanced (Low Priority)
- LOW-001 through LOW-005 as needed

**Timeline**: TBD

---

## Notes

- All features should maintain hexagonal architecture principles
- Event-sourcing approach for tenant/namespace/permissions
- System-wide users with tenant associations
- URL path structure: `/tenants/{tenantId}/namespaces/{namespaceId}/...`
- Comprehensive test coverage required for all features
- Documentation required for all features
