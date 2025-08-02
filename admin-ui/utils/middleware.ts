import { Handlers } from "$fresh/server.ts";
import { AuthService } from "./auth.ts";

export function withAuth(handler: Handlers): Handlers {
  return {
    ...handler,
    async GET(req, ctx) {
      const url = new URL(req.url);
      
      // Skip auth for login page
      if (url.pathname === "/login") {
        const result = await handler.GET?.(req, ctx);
        return result || new Response("Not Found", { status: 404 });
      }

      const authCookie = req.headers.get("cookie")?.match(/auth=([^;]+)/)?.[1];
      
      if (!authCookie) {
        return new Response("", {
          status: 302,
          headers: { Location: "/login" },
        });
      }

      const authService = AuthService.getInstance();
      // Check if the username exists in our users list
      if (!(await authService.hasUser(authCookie))) {
        return new Response("", {
          status: 302,
          headers: { Location: "/login" },
        });
      }

      const result = await handler.GET?.(req, ctx);
      return result || new Response("Not Found", { status: 404 });
    },

    async POST(req, ctx) {
      const url = new URL(req.url);
      
      // Skip auth for login page
      if (url.pathname === "/login") {
        const result = await handler.POST?.(req, ctx);
        return result || new Response("Not Found", { status: 404 });
      }

      const authCookie = req.headers.get("cookie")?.match(/auth=([^;]+)/)?.[1];
      
      if (!authCookie) {
        return new Response("", {
          status: 302,
          headers: { Location: "/login" },
        });
      }

      const authService = AuthService.getInstance();
      // Check if the username exists in our users list
      if (!(await authService.hasUser(authCookie))) {
        return new Response("", {
          status: 302,
          headers: { Location: "/login" },
        });
      }

      const result = await handler.POST?.(req, ctx);
      return result || new Response("Not Found", { status: 404 });
    },
  };
} 