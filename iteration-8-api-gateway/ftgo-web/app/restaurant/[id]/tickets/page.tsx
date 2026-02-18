"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { kitchenApi, restaurantsApi, type KitchenTicket, type Restaurant } from "@/lib/api";
import OrderStatusBadge from "@/components/OrderStatusBadge";
import WorkflowStepper from "@/components/WorkflowStepper";

const TICKET_STEPS = ["CREATED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP"];

function getSteps(status: string) {
  const idx = TICKET_STEPS.indexOf(status);
  return TICKET_STEPS.map((s, i) => ({
    label: s.replace(/_/g, " "),
    done: i < idx,
    active: i === idx,
  }));
}

export default function TicketsPage() {
  const params = useParams();
  const restaurantId = Number(params.id);

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [tickets, setTickets] = useState<KitchenTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  const fetchData = () => {
    Promise.all([
      restaurantsApi.getById(restaurantId),
      kitchenApi.getTickets(restaurantId),
    ])
      .then(([r, t]) => {
        setRestaurant(r);
        setTickets(t);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 15000);
    return () => clearInterval(timer);
  }, [restaurantId]);

  const handleAction = async (action: (id: number) => Promise<KitchenTicket>, ticketId: number) => {
    setActionError("");
    try {
      await action(ticketId);
      fetchData();
    } catch (e: any) {
      setActionError(e.message);
    }
  };

  if (loading) return <div className="text-center py-12 text-gray-500">Loading tickets...</div>;
  if (error) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {restaurant?.name} - Kitchen Tickets
          </h1>
          <p className="text-gray-500 text-sm mt-1">Auto-refreshes every 15 seconds</p>
        </div>
        <Link href="/restaurant" className="text-orange-600 hover:underline text-sm font-medium">
          Back to Dashboard
        </Link>
      </div>

      {/* Workflow */}
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <WorkflowStepper
          steps={TICKET_STEPS.map((s) => ({
            label: s.replace(/_/g, " "),
            done: false,
            active: false,
          }))}
        />
      </div>

      {actionError && (
        <div className="bg-red-50 text-red-700 p-4 rounded-lg mb-4">{actionError}</div>
      )}

      {tickets.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          No kitchen tickets yet. Place an order as a consumer first.
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow-md overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ticket #</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order #</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Items</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Created</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {tickets.map((ticket) => (
                <tr key={ticket.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    #{ticket.id}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                    #{ticket.orderId}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600 max-w-xs truncate">
                    {ticket.items}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <OrderStatusBadge status={ticket.status} />
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {new Date(ticket.createdAt).toLocaleTimeString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex space-x-2">
                      {ticket.status === "CREATED" && (
                        <button
                          onClick={() => handleAction(kitchenApi.accept, ticket.id)}
                          className="bg-blue-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-blue-600"
                        >
                          Accept
                        </button>
                      )}
                      {ticket.status === "ACCEPTED" && (
                        <button
                          onClick={() => handleAction(kitchenApi.preparing, ticket.id)}
                          className="bg-indigo-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-indigo-600"
                        >
                          Start Preparing
                        </button>
                      )}
                      {ticket.status === "PREPARING" && (
                        <button
                          onClick={() => handleAction(kitchenApi.ready, ticket.id)}
                          className="bg-green-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-green-600"
                        >
                          Mark Ready
                        </button>
                      )}
                      {ticket.status === "READY_FOR_PICKUP" && (
                        <span className="text-green-600 text-xs font-medium">Done</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
