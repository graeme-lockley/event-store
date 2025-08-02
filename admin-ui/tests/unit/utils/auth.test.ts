import { assertEquals } from "$std/assert/mod.ts";
import { describe, it, beforeEach, afterEach } from "$std/testing/bdd.ts";
import { AuthService, MemoryUserStorage } from "../../../utils/auth.ts";

const ADMIN_USER = "admin";
const ADMIN_PASS = "admin123";

const TEST_USER = "testuser";
const TEST_PASS = "testpass";

describe("AuthService", () => {
  let authService: AuthService;
  let memoryStorage: MemoryUserStorage;

  beforeEach(async () => {
    memoryStorage = new MemoryUserStorage();
    authService = AuthService.createForTesting(memoryStorage);
    await authService.addUser(ADMIN_USER, ADMIN_PASS);
  });

  afterEach(() => {
    memoryStorage.reset();
  });

  describe("getInstance", () => {
    it("should return the same instance", () => {
      const instance1 = AuthService.getInstance();
      const instance2 = AuthService.getInstance();
      assertEquals(instance1, instance2);
    });
  });

  describe("authenticate", () => {
    it("should authenticate valid credentials", async () => {
      const result = await authService.authenticate(ADMIN_USER, ADMIN_PASS);
      assertEquals(result, true);
    });

    it("should reject invalid credentials", async () => {
      const result = await authService.authenticate(ADMIN_USER, `wrong${ADMIN_PASS}`);
      assertEquals(result, false);
    });

    it("should reject non-existent user", async () => {
      const result = await authService.authenticate("nonexistent", "password");
      assertEquals(result, false);
    });
  });

  describe("hasUser", () => {
    it("should return true for valid user", async () => {
      const hasUser = await authService.hasUser(ADMIN_USER);
      assertEquals(hasUser, true);
    });

    it("should return false for invalid user", async () => {
      const hasUser = await authService.hasUser("nonexistent");
      assertEquals(hasUser, false);
    });
  });

  describe("addUser", () => {
    it("should add new user", async () => {
      const result = await authService.addUser(TEST_USER, TEST_PASS);
      assertEquals(result, true);
      
      const hasUser = await authService.hasUser(TEST_USER);
      assertEquals(hasUser, true);
    });

    it("should allow authentication for added user", async () => {
      await authService.addUser(TEST_USER, TEST_PASS);
      const canAuthenticate = await authService.authenticate(TEST_USER, TEST_PASS);
      assertEquals(canAuthenticate, true);
    });

    it("should not add duplicate user", async () => {
      await authService.addUser(TEST_USER, TEST_PASS);
      const result = await authService.addUser(TEST_USER, TEST_PASS);
      assertEquals(result, false);
    });
  });

  describe("removeUser", () => {
    it("should remove existing user", async () => {
      await authService.addUser(TEST_USER, TEST_PASS);
      assertEquals(await authService.hasUser(TEST_USER), true);

      const result = await authService.removeUser(TEST_USER);
      assertEquals(result, true);
      assertEquals(await authService.hasUser(TEST_USER), false);
    });

    it("should not affect other users when removing user", async () => {
      await authService.addUser(TEST_USER, TEST_PASS);
      await authService.addUser("otheruser", "otherpass");
      
      await authService.removeUser(TEST_USER);

      // Other user should still exist
      assertEquals(await authService.hasUser("otheruser"), true);
      assertEquals(await authService.hasUser(TEST_USER), false);
    });

    it("should return false when removing non-existent user", async () => {
      const result = await authService.removeUser("nonexistent");
      assertEquals(result, false);
    });

    it("should not remove admin user", async () => {
      const result = await authService.removeUser(ADMIN_USER);
      assertEquals(result, false);
    });
  });

  describe("MemoryUserStorage", () => {
    it("should maintain separate state for different instances", async () => {
      const storage1 = new MemoryUserStorage();
      const storage2 = new MemoryUserStorage();
      
      await storage1.saveUsers([{
        username: "user1",
        passwordHash: "hash1",
        createdAt: new Date().toISOString(),
      }]);
      
      await storage2.saveUsers([{
        username: "user2",
        passwordHash: "hash2",
        createdAt: new Date().toISOString(),
      }]);

      const users1 = await storage1.loadUsers();
      const users2 = await storage2.loadUsers();
      
      assertEquals(users1.length, 1);
      assertEquals(users2.length, 1);
      assertEquals(users1[0].username, "user1");
      assertEquals(users2[0].username, "user2");
    });

    it("should reset state correctly", async () => {
      await memoryStorage.saveUsers([{
        username: "testuser",
        passwordHash: "hash",
        createdAt: new Date().toISOString(),
      }]);
      
      assertEquals(memoryStorage.getUsers().length, 1);
      
      memoryStorage.reset();
      assertEquals(memoryStorage.getUsers().length, 0);
    });
  });

  describe("File-based storage with atomic operations", () => {
    it("should handle file-based storage correctly", async () => {
      // Test with file-based storage (default)
      const fileAuthService = AuthService.getInstance();
      
      // Add a test user
      const result = await fileAuthService.addUser("fileuser", "filepass");
      assertEquals(result, true);
      
      // Verify user exists
      const hasUser = await fileAuthService.hasUser("fileuser");
      assertEquals(hasUser, true);
      
      // Test authentication
      const canAuth = await fileAuthService.authenticate("fileuser", "filepass");
      assertEquals(canAuth, true);
      
      // Clean up
      await fileAuthService.removeUser("fileuser");
    });
  });
}); 