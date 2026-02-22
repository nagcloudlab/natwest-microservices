import { getServiceUrl, proxyRequest } from "@/lib/proxy";

export async function PUT(request: Request, { params }: { params: { id: string } }) {
  return proxyRequest(
    `${getServiceUrl("deliveries")}/api/deliveries/${params.id}/deliver`,
    request,
    "PUT"
  );
}
