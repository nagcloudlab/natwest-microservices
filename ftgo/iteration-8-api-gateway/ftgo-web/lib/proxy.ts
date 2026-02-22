import { NextResponse } from "next/server";

const GATEWAY_URL = process.env.API_GATEWAY_URL || "http://localhost:8090";

export function getServiceUrl(_service: string): string {
  return GATEWAY_URL;
}

export async function proxyRequest(
  upstreamUrl: string,
  request: Request,
  method?: string
): Promise<NextResponse> {
  const options: RequestInit = {
    method: method || request.method,
    headers: { "Content-Type": "application/json" },
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
