import { getServiceUrl, proxyGet } from "@/lib/proxy";
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const qs = searchParams.toString();
  const url = `${getServiceUrl("kitchen")}/api/kitchen/tickets${qs ? `?${qs}` : ""}`;
  return proxyGet(url);
}
