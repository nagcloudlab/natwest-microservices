import { getServiceUrl, proxyRequest } from "@/lib/proxy";

export async function PUT(request: Request, { params }: { params: { id: string } }) {
  return proxyRequest(`${getServiceUrl("orders")}/api/orders/${params.id}/cancel`, request, "PUT");
}
