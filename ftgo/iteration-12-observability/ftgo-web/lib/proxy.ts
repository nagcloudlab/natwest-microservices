import { NextResponse } from "next/server";
import { cookies } from "next/headers";

const GATEWAY_URL = process.env.API_GATEWAY_URL || "http://localhost:8090";

export function getServiceUrl(_service: string): string {
  return GATEWAY_URL;
}

export function getAuthHeaders(): Record<string, string> {
  const cookieStore = cookies();
  const token = cookieStore.get("ftgo_token")?.value;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

export async function proxyRequest(
  upstreamUrl: string,
  request: Request,
  method?: string
): Promise<NextResponse> {
  const options: RequestInit = {
    method: method || request.method,
    headers: getAuthHeaders(),
    cache: "no-store",
  };

  if (["POST", "PUT", "PATCH"].includes(options.method!)) {
    try {
      const body = await request.text();
      if (body) options.body = body;
    } catch {
      // no body
    }
  }

  const res = await fetch(upstreamUrl, options);
  const data = await res.text();

  return new NextResponse(data, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function proxyGet(upstreamUrl: string): Promise<Response> {
  const res = await fetch(upstreamUrl, {
    cache: "no-store",
    headers: getAuthHeaders(),
  });
  const data = await res.text();
  return new Response(data, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}
