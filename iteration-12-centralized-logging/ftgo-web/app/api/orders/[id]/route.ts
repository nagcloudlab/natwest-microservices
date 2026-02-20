import { getServiceUrl } from "@/lib/proxy";
import { NextResponse } from "next/server";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("orders")}/api/orders/${params.id}`;
  const res = await fetch(url, { cache: "no-store" });
  const data = await res.text();
  return new NextResponse(data, { status: res.status, headers: { "Content-Type": "application/json" } });
}
