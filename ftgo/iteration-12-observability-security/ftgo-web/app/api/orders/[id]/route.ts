import { getServiceUrl, proxyGet } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("orders")}/api/orders/${params.id}`;
  return proxyGet(url);
}
