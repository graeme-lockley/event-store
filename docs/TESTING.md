# 🧪 Testing Strategy for Event Store Admin UI

## 📋 Overview

This document outlines the comprehensive testing strategy for the Event Store Admin UI, ensuring code quality, reliability, and maintainability.

---

## 🎯 Testing Goals

- **Code Quality**: Ensure all code is tested and working correctly
- **Regression Prevention**: Catch bugs before they reach production
- **Documentation**: Tests serve as living documentation
- **Confidence**: Enable safe refactoring and feature additions
- **CI/CD**: Automated testing for continuous integration

---

## 🏗️ Testing Architecture

### Test Types

#### 1. **Unit Tests** (`tests/unit/`)
- **Purpose**: Test individual functions and components in isolation
- **Coverage**: Utils, helpers, pure functions, service logic
- **Tools**: Deno testing, standard library assertions
- **Speed**: Fast execution (< 1 second per test)

#### 2. **Integration Tests** (`tests/integration/`)
- **Purpose**: Test API endpoints and data flow
- **Coverage**: Route handlers, API responses, Event Store integration
- **Tools**: Deno testing, HTTP client testing
- **Speed**: Medium execution (1-5 seconds per test)

#### 3. **Component Tests**
- **Purpose**: Test Preact components and islands
- **Coverage**: Component rendering, props, events
- **Tools**: Preact testing utilities, DOM testing
- **Speed**: Fast execution (< 1 second per test)

#### 4. **E2E Tests**
- **Purpose**: Test complete user workflows
- **Coverage**: Full application flows, user interactions
- **Tools**: Playwright, browser automation
- **Speed**: Slow execution (5-30 seconds per test)

---

## 📁 Test Structure

```
tests/
├── unit/                    # Unit tests
│   └── utils/              # Utility function tests
│       ├── auth.test.ts    # Authentication service tests
│       └── eventStore.test.ts # Event Store client tests
├── integration/            # Integration tests
│   ├── api/                # API endpoint tests
│   │   ├── stores.test.ts  # Store management API tests
│   │   └── test-connection.test.ts # Connection testing API
│   └── event-store.test.ts # Event Store API integration
├── helpers/                # Test utilities and helpers
│   ├── integration_server.ts # Integration test server setup
│   ├── setup.ts            # Test setup utilities
│   └── test-setup.ts       # Component test setup
└── fixtures/               # Test data and fixtures
```

---

## 🛠️ Testing Tools & Dependencies

### Core Testing Framework
- **Deno Testing**: Built-in testing framework
- **Standard Library**: Assertions, mocking, HTTP testing

### Component Testing
- **Preact Testing**: Component rendering and interaction
- **DOM Testing**: Browser-like environment for components

### E2E Testing
- **Playwright**: Browser automation and testing
- **Visual Regression**: Screenshot comparison

### Test Utilities
- **Integration Server**: Real Event Store and Admin UI servers for testing
- **Test Helpers**: Common testing utilities
- **Fixtures**: Reusable test data

---

## 🚀 Test Commands

```bash
# Run all tests
deno task test

# Run specific test types
deno task test:unit
deno task test:integration
deno task test:e2e

# Watch mode for development
deno task test:watch

# Generate coverage report
deno task test:coverage

# CI/CD testing
deno task test:ci
```

---

## 📊 Test Coverage Requirements

### Current Coverage
- **Unit Tests**: Authentication service, Event Store client
- **Integration Tests**: Store management API, connection testing, Event Store integration
- **Component Tests**: Component rendering and interaction testing
- **E2E Tests**: Full user workflow testing

### Coverage Exclusions
- Generated files (`fresh.gen.ts`)
- Static assets
- Configuration files
- Third-party dependencies

---

## 🧩 Testing Patterns

### Unit Test Pattern
```typescript
import { assertEquals, assertExists } from "$std/assert/mod.ts";
import { describe, it } from "$std/testing/bdd.ts";

describe("Utility Function", () => {
  it("should handle valid input", () => {
    const result = utilityFunction("valid input");
    assertEquals(result, "expected output");
  });

  it("should handle invalid input", () => {
    const result = utilityFunction("invalid input");
    assertEquals(result, null);
  });
});
```

### Integration Test Pattern
```typescript
import { assertEquals } from "$std/assert/mod.ts";
import { describe, it, beforeAll, afterAll } from "$std/testing/bdd.ts";
import { startIntegrationServers } from "../../helpers/integration_server.ts";

describe("API Endpoint", () => {
  let servers: Awaited<ReturnType<typeof startIntegrationServers>>;
  let baseUrl: string;

  beforeAll(async () => {
    servers = await startIntegrationServers();
    baseUrl = servers.adminUI.url;
  });

  afterAll(async () => {
    await servers.stop();
  });

  it("should return correct response", async () => {
    const response = await fetch(`${baseUrl}/api/test`);
    assertEquals(response.status, 200);
    
    const data = await response.json();
    assertEquals(data.success, true);
  });
});
```

### Component Test Pattern
```typescript
import { assertEquals } from "$std/assert/mod.ts";
import { describe, it } from "$std/testing/bdd.ts";
import { render } from "preact-render-to-string";
import Component from "../../components/Component.tsx";

describe("Component", () => {
  it("should render correctly", () => {
    const html = render(<Component prop="value" />);
    assertEquals(html.includes("expected text"), true);
  });
});
```

### E2E Test Pattern
```typescript
import { assertEquals } from "$std/assert/mod.ts";
import { describe, it } from "$std/testing/bdd.ts";

describe("User Workflow", () => {
  it("should complete login flow", async () => {
    // Navigate to login page
    // Fill in credentials
    // Submit form
    // Verify redirect to dashboard
    assertEquals(true, true); // Placeholder
  });
});
```

---

## 🔧 Test Configuration

### Environment Setup
```typescript
// tests/helpers/integration_server.ts
export const TEST_CONFIG = {
  adminUIPort: 18002,
  eventStorePort: 18000,
  testDataDir: "./test-data"
};
```

### Test Data
```typescript
// tests/fixtures/stores.ts
export const mockStores = [
  {
    name: "Test Store",
    url: "http://localhost:8000",
    port: 8000
  }
];
```

### Test Helpers
```typescript
// tests/helpers/integration_server.ts
export async function startIntegrationServers() {
  // Start real Event Store and Admin UI servers for testing
}

// tests/helpers/setup.ts
export async function createTestStore(store: StoreConfig) {
  // Helper to create test store
}

export async function cleanupTestData() {
  // Helper to clean up test data
}
```

---

## 🎭 Testing Strategy

### Integration Testing
- **Real Event Store**: Use actual Event Store server for integration tests
- **Real Admin UI**: Use actual Admin UI server for API testing
- **Isolated Data**: Each test uses isolated data directories

### Unit Testing
- **Service Isolation**: Test services with mock storage
- **Pure Functions**: Test utility functions in isolation
- **Error Handling**: Test error scenarios and edge cases

### Component Testing
- **Props**: Mock component props for testing
- **Events**: Mock user interactions and events
- **Context**: Mock Fresh context and state

---

## 🔄 Test Data Management

### Test Data Strategy
- **Isolation**: Each test uses isolated data directories
- **Cleanup**: Automatic cleanup after tests via integration server
- **Fixtures**: Reusable test data sets
- **Factories**: Dynamic test data generation

### Data Cleanup
```typescript
// tests/helpers/integration_server.ts
export async function cleanupTestData() {
  // Remove test data directories
  // Stop test servers
  // Reset test state
}
```

---

## 📈 Continuous Integration

### CI Pipeline
1. **Lint**: Code style and quality checks
2. **Type Check**: TypeScript compilation
3. **Unit Tests**: Fast unit test execution
4. **Integration Tests**: API and integration testing
5. **E2E Tests**: Full application testing
6. **Coverage Report**: Generate coverage metrics
7. **Deploy**: Deploy if all tests pass

### Pre-commit Hooks
- Run unit tests
- Check code formatting
- Verify TypeScript types
- Run linting

---

## 🐛 Debugging Tests

### Common Issues
- **Async/Await**: Proper async test handling
- **Timing**: Race conditions in tests
- **Isolation**: Test data conflicts
- **Environment**: Missing environment variables

### Debug Commands
```bash
# Debug specific test
deno test --allow-all --inspect-brk tests/unit/specific_test.ts

# Verbose output
deno test --allow-all --verbose

# Stop on first failure
deno test --allow-all --fail-fast
```

---

## 📚 Test Documentation

### Test Naming Conventions
- **Unit Tests**: `functionName.test.ts`
- **Integration Tests**: `endpointName.integration.test.ts`
- **E2E Tests**: `workflowName.e2e.test.ts`
- **Component Tests**: `ComponentName.component.test.ts`

### Test Descriptions
- Use descriptive test names
- Explain the "why" not just the "what"
- Group related tests with `describe` blocks
- Use `it` blocks for individual test cases

---

## 🎯 Implementation Priority

### Phase 1: Foundation ✅ (Completed)
1. ✅ Set up testing infrastructure
2. ✅ Create test helpers and utilities
3. ✅ Add unit tests for utility functions
4. ✅ Configure CI/CD pipeline

### Phase 2: Core Features ✅ (Completed)
1. ✅ Add integration tests for API endpoints
2. ✅ Test authentication flows
3. ✅ Test store management features
4. 🔄 Add component tests for islands (In Progress)

### Phase 3: E2E Testing
1. Implement E2E test framework
2. Test complete user workflows
3. Add visual regression tests
4. Performance testing

### Phase 4: Advanced Testing
1. Add stress testing
2. Security testing
3. Accessibility testing
4. Cross-browser testing

---

## 📊 Success Metrics

### Quality Metrics
- **Test Coverage**: Unit and integration tests implemented
- **Test Reliability**: Stable test execution
- **Test Speed**: < 2 seconds for full suite
- **Bug Detection**: Catch bugs before production

### Process Metrics
- **Test Maintenance**: Low maintenance overhead
- **Test Documentation**: Well-documented test patterns
- **CI/CD Success**: Automated testing pipeline

---

*This testing strategy ensures the Event Store Admin UI is robust, reliable, and maintainable.* 