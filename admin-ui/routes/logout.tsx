import { Handlers } from "$fresh/server.ts";

export const handler: Handlers = {
  GET() {
    const headers = new Headers();
    headers.set("Set-Cookie", "auth=; Path=/; HttpOnly; Max-Age=0");
    headers.set("Location", "/login");

    return new Response("", {
      status: 302,
      headers,
    });
  },
};
