import { getServiceUrl, proxyGet } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${getServiceUrl("deliveries")}/api/deliveries`;
  return proxyGet(url);
}
