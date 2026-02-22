import { getServiceUrl, proxyGet, proxyRequest } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${getServiceUrl("orders")}/api/orders`;
  return proxyGet(url);
}

export async function POST(request: Request) {
  return proxyRequest(`${getServiceUrl("orders")}/api/orders`, request);
}
