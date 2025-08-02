const ADMIN_USER = "admin";
const DEFAULT_ADMIN_PASS = "admin";

// Simple authentication utility
export interface User {
  username: string;
  password: string;
}

// User interface (matches the API)
interface StoredUser {
  username: string;
  passwordHash: string;
  createdAt: string;
}

// Storage interface for abstraction
interface UserStorage {
  loadUsers(): Promise<StoredUser[]>;
  saveUsers(users: StoredUser[]): Promise<void>;
}

// Simple password hashing function using built-in crypto
async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(password);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  return btoa(String.fromCharCode(...new Uint8Array(hashBuffer)));
}

// Simple password comparison function
async function comparePassword(password: string, hash: string): Promise<boolean> {
  const passwordHash = await hashPassword(password);
  return passwordHash === hash;
}

// File-based implementation with atomic operations
class FileUserStorage implements UserStorage {
  private readonly USERS_FILE = "./data/users.json";

  private async ensureDataDirectory() {
    try {
      await Deno.mkdir("./data", { recursive: true });
    } catch (_) {
      // Directory might already exist, ignore error
    }
  }

  async loadUsers(): Promise<StoredUser[]> {
    try {
      await this.ensureDataDirectory();
      const fileContent = await Deno.readTextFile(this.USERS_FILE);
      const users = JSON.parse(fileContent);
      return Array.isArray(users) ? users : [];
    } catch (error) {
      // File doesn't exist or is invalid, return default admin user
      console.log(`No users file found in AuthService, creating default ${ADMIN_USER} user:`, error);
      const defaultUser: StoredUser = {
        username: ADMIN_USER,
        passwordHash: await hashPassword(DEFAULT_ADMIN_PASS),
        createdAt: new Date().toISOString(),
      };
      await this.saveUsers([defaultUser]);
      return [defaultUser];
    }
  }

  async saveUsers(users: StoredUser[]): Promise<void> {
    try {
      await this.ensureDataDirectory();
      const fileContent = JSON.stringify(users, null, 2);
      
      // Atomic file operation: write to temp file first, then rename
      const tempFile = this.USERS_FILE + ".tmp";
      await Deno.writeTextFile(tempFile, fileContent);
      await Deno.rename(tempFile, this.USERS_FILE);
    } catch (error) {
      console.error("Failed to save users from AuthService:", error);
      throw new Error("Failed to save users to file");
    }
  }
}

// Memory-based implementation for testing
export class MemoryUserStorage implements UserStorage {
  private users: StoredUser[] = [];

  async loadUsers(): Promise<StoredUser[]> {
    return [...this.users]; // Return a copy
  }

  async saveUsers(users: StoredUser[]): Promise<void> {
    this.users = [...users]; // Store a copy
  }

  // Test helper to reset state
  reset(): void {
    this.users = [];
  }

  // Test helper to get current users
  getUsers(): StoredUser[] {
    return [...this.users];
  }
}

export class AuthService {
  private static instance: AuthService;
  private storage: UserStorage;

  private constructor(storage: UserStorage) {
    this.storage = storage;
  }

  static getInstance(storage?: UserStorage): AuthService {
    if (!AuthService.instance) {
      AuthService.instance = new AuthService(storage || new FileUserStorage());
    }
    return AuthService.instance;
  }

  // For testing - allow injection of different storage
  static createForTesting(storage: UserStorage): AuthService {
    return new AuthService(storage);
  }

  async authenticate(username: string, password: string): Promise<boolean> {
    try {
      const users = await this.storage.loadUsers();
      const user = users.find(u => u.username === username);
      if (!user) return false;
      
      return await comparePassword(password, user.passwordHash);
    } catch (error) {
      console.error("Authentication error:", error);
      return false;
    }
  }

  async hasUser(username: string): Promise<boolean> {
    try {
      const users = await this.storage.loadUsers();
      return users.some(user => user.username === username);
    } catch (error) {
      console.error("Error checking user existence:", error);
      return false;
    }
  }

  // Add a user (for API integration)
  async addUser(username: string, password: string): Promise<boolean> {
    try {
      const users = await this.storage.loadUsers();
      
      // Check if user already exists
      if (users.some(u => u.username === username)) {
        return false;
      }

      // Create new user
      const newUser: StoredUser = {
        username,
        passwordHash: await hashPassword(password),
        createdAt: new Date().toISOString(),
      };

      users.push(newUser);
      await this.storage.saveUsers(users);
      return true;
    } catch (error) {
      console.error("Failed to add user:", error);
      return false;
    }
  }

  // Remove a user (for API integration)
  async removeUser(username: string): Promise<boolean> {
    try {
      if (username === ADMIN_USER) {
        return false;
      }

      const users = await this.storage.loadUsers();
      const filteredUsers = users.filter(u => u.username !== username);
      
      if (filteredUsers.length === users.length) {
        return false; // User not found
      }

      await this.storage.saveUsers(filteredUsers);
      return true;
    } catch (error) {
      console.error("Failed to remove user:", error);
      return false;
    }
  }
}
