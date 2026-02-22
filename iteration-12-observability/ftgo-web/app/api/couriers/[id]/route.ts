import { getServiceUrl, proxyGet } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("deliveries")}/api/deliveries/couriers/${params.id}`;
  return proxyGet(url);
}
