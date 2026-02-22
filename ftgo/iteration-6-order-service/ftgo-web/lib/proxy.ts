import { NextResponse } from "next/server";

const SERVICE_URLS: Record<string, string> = {
  orders: process.env.ORDER_SERVICE_URL || "http://localhost:8080",
  restaurants: process.env.RESTAURANT_SERVICE_URL || "http://localhost:8081",
  notifications: process.env.NOTIFICATION_SERVICE_URL || "http://localhost:8082",
  payments: process.env.ACCOUNTING_SERVICE_URL || "http://localhost:8083",
  kitchen: process.env.KITCHEN_SERVICE_URL || "http://localhost:8084",
  deliveries: process.env.DELIVERY_SERVICE_URL || "http://localhost:8085",
  couriers: process.env.DELIVERY_SERVICE_URL || "http://localhost:8085",
};

export function getServiceUrl(service: string): string {
  return SERVICE_URLS[service] || "http://localhost:8080";
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
