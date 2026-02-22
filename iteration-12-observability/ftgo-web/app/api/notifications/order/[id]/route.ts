import { getServiceUrl, proxyGet } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("notifications")}/api/notifications/order/${params.id}`;
  return proxyGet(url);
}
