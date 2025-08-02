// Test configuration and setup helpers
export const TEST_CONFIG = {
  baseUrl: "http://localhost:8001",
  eventStoreUrl: "http://localhost:8000",
  testUser: {
    username: "testuser",
    password: "testpass"
  },
  adminUser: {
    username: "admin",
    password: "admin123"
  }
};

export const TEST_STORES = [
  {
    name: "Local Event Store",
    url: "http://localhost:8000",
    port: 8000
  },
  {
    name: "Test Event Store",
    url: "http://localhost:8001",
    port: 8001
  }
];

export const TEST_TOPICS = [
  {
    name: "user-events",
    schemas: [
      {
        type: "user.created",
        properties: {
          id: { type: "string" },
          name: { type: "string" }
        },
        required: ["id", "name"]
      }
    ]
  },
  {
    name: "audit-events",
    schemas: [
      {
        type: "audit.log",
        properties: {
          action: { type: "string" },
          timestamp: { type: "string" }
        },
        required: ["action", "timestamp"]
      }
    ]
  }
];

export const TEST_EVENTS = [
  {
    topic: "user-events",
    type: "user.created",
    payload: {
      id: "123",
      name: "Test User"
    }
  },
  {
    topic: "audit-events",
    type: "audit.log",
    payload: {
      action: "user.login",
      timestamp: new Date().toISOString()
    }
  }
];

// Test utilities
export async function waitForServer(url: string, timeout = 5000): Promise<boolean> {
  const startTime = Date.now();
  
  while (Date.now() - startTime < timeout) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        return true;
      }
    } catch (error) {
      // Server not ready yet
    }
    
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  
  return false;
}

export async function createTestStore(store: typeof TEST_STORES[0]): Promise<boolean> {
  try {
    const response = await fetch(`${TEST_CONFIG.baseUrl}/api/stores`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(store),
    });
    
    return response.ok;
  } catch (error) {
    return false;
  }
}

export async function removeTestStore(storeName: string): Promise<boolean> {
  try {
    const response = await fetch(`${TEST_CONFIG.baseUrl}/api/stores/${encodeURIComponent(storeName)}`, {
      method: "DELETE",
    });
    
    return response.ok;
  } catch (error) {
    return false;
  }
}

export async function cleanupTestData(): Promise<void> {
  // Remove test stores
  for (const store of TEST_STORES) {
    await removeTestStore(store.name);
  }
  
  // Add small delay to ensure cleanup completes
  await new Promise(resolve => setTimeout(resolve, 100));
}

export function generateTestId(): string {
  return `test-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export function createMockEventStoreResponse(data: any): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
    },
  });
}

export function createMockErrorResponse(status: number, message: string): Response {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: {
      "Content-Type": "application/json",
    },
  });
} 