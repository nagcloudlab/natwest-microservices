"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { deliveriesApi, type Delivery, type Courier } from "@/lib/api";
import OrderStatusBadge from "@/components/OrderStatusBadge";
import WorkflowStepper from "@/components/WorkflowStepper";

const DELIVERY_STEPS = ["PENDING", "COURIER_ASSIGNED", "PICKED_UP", "DELIVERED"];

function getSteps(status: string) {
  const idx = DELIVERY_STEPS.indexOf(status);
  return DELIVERY_STEPS.map((s, i) => ({
    label: s.replace(/_/g, " "),
    done: i < idx,
    active: i === idx,
  }));
}

export default function CourierDeliveriesPage() {
  const params = useParams();
  const courierId = Number(params.id);

  const [courier, setCourier] = useState<Courier | null>(null);
  const [myDeliveries, setMyDeliveries] = useState<Delivery[]>([]);
  const [pendingDeliveries, setPendingDeliveries] = useState<Delivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  const fetchData = () => {
    Promise.all([
      deliveriesApi.getCourier(courierId).catch(() => null),
      deliveriesApi.getByCourier(courierId),
      deliveriesApi.getAll(),
    ])
      .then(([c, mine, all]) => {
        setCourier(c);
        setMyDeliveries(mine);
        setPendingDeliveries(all.filter((d) => d.status === "PENDING"));
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 15000);
    return () => clearInterval(timer);
  }, [courierId]);

  const handleAction = async (action: () => Promise<any>) => {
    setActionError("");
    try {
      await action();
      fetchData();
    } catch (e: any) {
      setActionError(e.message);
    }
  };

  if (loading) return <div className="text-center py-12 text-gray-500">Loading deliveries...</div>;
  if (error) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {courier?.name || `Courier #${courierId}`} - Deliveries
          </h1>
          <p className="text-gray-500 text-sm mt-1">Auto-refreshes every 15 seconds</p>
        </div>
        <Link href="/courier" className="text-orange-600 hover:underline text-sm font-medium">
          Back to Dashboard
        </Link>
      </div>

      {/* Workflow */}
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <WorkflowStepper
          steps={DELIVERY_STEPS.map((s) => ({
            label: s.replace(/_/g, " "),
            done: false,
            active: false,
          }))}
        />
      </div>

      {actionError && (
        <div className="bg-red-50 text-red-700 p-4 rounded-lg mb-4">{actionError}</div>
      )}

      {/* My Deliveries */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">My Deliveries</h2>
        {myDeliveries.length === 0 ? (
          <div className="text-center py-8 text-gray-500 bg-white rounded-lg shadow-sm">
            No deliveries assigned to you yet.
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order #</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Pickup</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Delivery</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {myDeliveries.map((d) => (
                  <tr key={d.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">#{d.id}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">#{d.orderId}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{d.pickupAddress}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{d.deliveryAddress}</td>
                    <td className="px-6 py-4">
                      <OrderStatusBadge status={d.status} />
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex space-x-2">
                        {d.status === "COURIER_ASSIGNED" && (
                          <button
                            onClick={() => handleAction(() => deliveriesApi.pickup(d.id))}
                            className="bg-cyan-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-cyan-600"
                          >
                            Pick Up
                          </button>
                        )}
                        {d.status === "PICKED_UP" && (
                          <button
                            onClick={() => handleAction(() => deliveriesApi.deliver(d.id))}
                            className="bg-green-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-green-600"
                          >
                            Deliver
                          </button>
                        )}
                        {d.status === "DELIVERED" && (
                          <span className="text-green-600 text-xs font-medium">Completed</span>
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

      {/* Pending Deliveries */}
      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Pending Deliveries</h2>
        {pendingDeliveries.length === 0 ? (
          <div className="text-center py-8 text-gray-500 bg-white rounded-lg shadow-sm">
            No pending deliveries available.
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order #</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Pickup</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Delivery</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {pendingDeliveries.map((d) => (
                  <tr key={d.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">#{d.id}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">#{d.orderId}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{d.pickupAddress}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{d.deliveryAddress}</td>
                    <td className="px-6 py-4">
                      <button
                        onClick={() =>
                          handleAction(() => deliveriesApi.assign(d.id, courierId))
                        }
                        className="bg-blue-500 text-white px-3 py-1 rounded text-xs font-medium hover:bg-blue-600"
                      >
                        Assign to Me
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
