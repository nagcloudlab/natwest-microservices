import { getServiceUrl, proxyRequest } from "@/lib/proxy";

export async function PUT(request: Request, { params }: { params: { id: string } }) {
  return proxyRequest(
    `${getServiceUrl("kitchen")}/api/kitchen/tickets/${params.id}/preparing`,
    request,
    "PUT"
  );
}
