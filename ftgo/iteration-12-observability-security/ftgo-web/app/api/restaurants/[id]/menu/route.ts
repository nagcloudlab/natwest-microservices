import { getServiceUrl, proxyGet } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("restaurants")}/api/restaurants/${params.id}/menu`;
  return proxyGet(url);
}
