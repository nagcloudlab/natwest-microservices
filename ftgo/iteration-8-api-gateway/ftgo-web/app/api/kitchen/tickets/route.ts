import { getServiceUrl } from "@/lib/proxy";
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const qs = searchParams.toString();
  const url = `${getServiceUrl("kitchen")}/api/kitchen/tickets${qs ? `?${qs}` : ""}`;
  const res = await fetch(url, { cache: "no-store" });
  const data = await res.text();
  return new Response(data, { status: res.status, headers: { "Content-Type": "application/json" } });
}
