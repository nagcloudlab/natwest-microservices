import { getServiceUrl } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${getServiceUrl("deliveries")}/api/deliveries/couriers`;
  const res = await fetch(url, { cache: "no-store" });
  const data = await res.text();
  return new Response(data, { status: res.status, headers: { "Content-Type": "application/json" } });
}
