import { getServiceUrl, proxyGet } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${getServiceUrl("restaurants")}/api/restaurants`;
  return proxyGet(url);
}
