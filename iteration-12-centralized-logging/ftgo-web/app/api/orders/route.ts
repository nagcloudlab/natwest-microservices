import { getServiceUrl, proxyRequest } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${getServiceUrl("orders")}/api/orders`;
  const res = await fetch(url, { cache: "no-store" });
  const data = await res.text();
  return new Response(data, { status: res.status, headers: { "Content-Type": "application/json" } });
}

export async function POST(request: Request) {
  return proxyRequest(`${getServiceUrl("orders")}/api/orders`, request);
}
