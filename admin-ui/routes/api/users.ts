import { Handlers } from "$fresh/server.ts";

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

// User interface
interface User {
  username: string;
  passwordHash: string;
  createdAt: string;
}

// File path for user storage
const USERS_FILE = "./data/users.json";

// Helper function to ensure data directory exists
async function ensureDataDirectory() {
  try {
    await Deno.mkdir("./data", { recursive: true });
  } catch (error) {
    // Directory might already exist, ignore error
  }
}

// Helper function to load users from file
async function loadUsers(): Promise<User[]> {
  try {
    await ensureDataDirectory();
    const fileContent = await Deno.readTextFile(USERS_FILE);
    const users = JSON.parse(fileContent);
    return Array.isArray(users) ? users : [];
  } catch (error) {
    // File doesn't exist or is invalid, return default admin user
    console.log("No users file found, creating default admin user");
    const defaultUser: User = {
      username: "admin",
      passwordHash: await hashPassword("admin"),
      createdAt: new Date().toISOString(),
    };
    await saveUsers([defaultUser]);
    return [defaultUser];
  }
}

// Helper function to save users to file
async function saveUsers(users: User[]): Promise<void> {
  try {
    await ensureDataDirectory();
    const fileContent = JSON.stringify(users, null, 2);
    await Deno.writeTextFile(USERS_FILE, fileContent);
    console.log("Users saved to file:", users.map(u => ({ username: u.username, createdAt: u.createdAt })));
  } catch (error) {
    console.error("Failed to save users:", error);
    throw new Error("Failed to save users to file");
  }
}

export const handler: Handlers = {
  async GET(req) {
    try {
      const users = await loadUsers();
      
      // Return list of users (without password hashes)
      const userList = users.map(user => ({
        username: user.username,
        createdAt: user.createdAt,
      }));
      
      return new Response(JSON.stringify({ users: userList }), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new Response(JSON.stringify({ 
        success: false, 
        error: errorMessage 
      }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },

  async POST(req) {
    try {
      // Check Content-Type header
      const contentType = req.headers.get("content-type");
      if (!contentType || !contentType.includes("application/json")) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Content-Type must be application/json" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      const body = await req.json();
      
      // Validate required fields
      if (!body.username || !body.password) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Username and password are required" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Load existing users
      const users = await loadUsers();

      // Check if user already exists
      const existingUser = users.find(u => u.username === body.username);
      if (existingUser) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "User already exists" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Validate password strength
      if (body.password.length < 6) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Password must be at least 6 characters long" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Create new user
      const newUser: User = {
        username: body.username,
        passwordHash: await hashPassword(body.password),
        createdAt: new Date().toISOString(),
      };

      users.push(newUser);
      await saveUsers(users);

      return new Response(JSON.stringify({
        success: true,
        message: `User ${body.username} created successfully`,
        user: {
          username: newUser.username,
          createdAt: newUser.createdAt,
        }
      }), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new Response(JSON.stringify({ 
        success: false, 
        error: errorMessage 
      }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },

  async PUT(req) {
    try {
      // Check Content-Type header
      const contentType = req.headers.get("content-type");
      if (!contentType || !contentType.includes("application/json")) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Content-Type must be application/json" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      const body = await req.json();
      
      if (!body.username || !body.currentPassword || !body.newPassword) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Username, current password, and new password are required" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Load existing users
      const users = await loadUsers();

      // Find user
      const user = users.find(u => u.username === body.username);
      if (!user) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "User not found" 
        }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Verify current password
      const isValidPassword = await comparePassword(body.currentPassword, user.passwordHash);
      if (!isValidPassword) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Current password is incorrect" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Validate new password strength
      if (body.newPassword.length < 6) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "New password must be at least 6 characters long" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Update password
      user.passwordHash = await hashPassword(body.newPassword);
      await saveUsers(users);

      return new Response(JSON.stringify({
        success: true,
        message: `Password updated successfully for ${body.username}`
      }), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new Response(JSON.stringify({ 
        success: false, 
        error: errorMessage 
      }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },

  async DELETE(req) {
    try {
      const url = new URL(req.url);
      const username = url.searchParams.get("username");
      
      if (!username) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Username is required" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Prevent deleting the admin user
      if (username === "admin") {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Cannot delete the admin user" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Load existing users
      const users = await loadUsers();

      // Find and remove user
      const userIndex = users.findIndex(u => u.username === username);
      if (userIndex === -1) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "User not found" 
        }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        });
      }

      users.splice(userIndex, 1);
      await saveUsers(users);

      return new Response(JSON.stringify({
        success: true,
        message: `User ${username} deleted successfully`
      }), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new Response(JSON.stringify({ 
        success: false, 
        error: errorMessage 
      }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },
}; 