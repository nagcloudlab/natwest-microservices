import { getServiceUrl, proxyGet } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const url = `${getServiceUrl("payments")}/api/payments/order/${params.id}`;
  return proxyGet(url);
}
