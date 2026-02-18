"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ordersApi, paymentsApi, notificationsApi, type OrderResponse, type Payment, type Notification } from "@/lib/api";
import OrderStatusBadge from "@/components/OrderStatusBadge";
import WorkflowStepper from "@/components/WorkflowStepper";

const ORDER_STEPS = ["PENDING", "APPROVED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERED"];

function getSteps(status: string) {
  const idx = ORDER_STEPS.indexOf(status);
  return ORDER_STEPS.map((s, i) => ({
    label: s.replace(/_/g, " "),
    done: i < idx,
    active: i === idx,
  }));
}

export default function OrderDetailPage() {
  const params = useParams();
  const orderId = Number(params.id);

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cancelling, setCancelling] = useState(false);

  const fetchData = () => {
    Promise.all([
      ordersApi.getById(orderId),
      paymentsApi.getByOrder(orderId).catch(() => null),
      notificationsApi.getByOrder(orderId).catch(() => []),
    ])
      .then(([o, p, n]) => {
        setOrder(o);
        setPayment(p);
        setNotifications(n);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 15000);
    return () => clearInterval(timer);
  }, [orderId]);

  const handleCancel = async () => {
    setCancelling(true);
    try {
      const updated = await ordersApi.cancel(orderId);
      setOrder(updated);
    } catch (e: any) {
      setError(e.message);
    }
    setCancelling(false);
  };

  if (loading) return <div className="text-center py-12 text-gray-500">Loading order...</div>;
  if (error && !order) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;
  if (!order) return <div className="text-center py-12 text-gray-500">Order not found.</div>;

  const canCancel = order.status === "PENDING" || order.status === "APPROVED";

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Order #{order.id}</h1>
        <Link href="/consumer/orders" className="text-orange-600 hover:underline text-sm font-medium">
          Back to Orders
        </Link>
      </div>

      {error && <div className="bg-red-50 text-red-700 p-4 rounded-lg mb-4">{error}</div>}

      {/* Workflow Stepper */}
      {order.status !== "CANCELLED" && order.status !== "REJECTED" && (
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h2 className="text-sm font-medium text-gray-500 mb-4">Order Progress</h2>
          <WorkflowStepper steps={getSteps(order.status)} />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Order Details */}
        <div className="bg-white rounded-lg shadow-md p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Order Details</h2>
          <dl className="space-y-3">
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">Status</dt>
              <dd><OrderStatusBadge status={order.status} /></dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">Customer</dt>
              <dd className="text-sm text-gray-900">{order.consumerName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">Restaurant</dt>
              <dd className="text-sm text-gray-900">{order.restaurantName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">Delivery Address</dt>
              <dd className="text-sm text-gray-900">{order.deliveryAddress}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">Created</dt>
              <dd className="text-sm text-gray-900">
                {new Date(order.createdAt).toLocaleString()}
              </dd>
            </div>
          </dl>

          {/* Items */}
          <h3 className="text-sm font-medium text-gray-700 mt-6 mb-2">Items</h3>
          <div className="border rounded-md divide-y">
            {order.items.map((item, i) => (
              <div key={i} className="flex justify-between px-4 py-2 text-sm">
                <span className="text-gray-700">
                  {item.quantity}x {item.menuItemName}
                </span>
                <span className="text-gray-900 font-medium">
                  ${(item.price * item.quantity).toFixed(2)}
                </span>
              </div>
            ))}
            <div className="flex justify-between px-4 py-2 text-sm font-semibold bg-gray-50">
              <span>Total</span>
              <span>${order.totalAmount.toFixed(2)}</span>
            </div>
          </div>

          {canCancel && (
            <button
              onClick={handleCancel}
              disabled={cancelling}
              className="mt-4 w-full bg-red-500 text-white py-2 px-4 rounded-md text-sm font-medium hover:bg-red-600 disabled:bg-gray-300 transition-colors"
            >
              {cancelling ? "Cancelling..." : "Cancel Order"}
            </button>
          )}
        </div>

        {/* Payment & Notifications */}
        <div className="space-y-6">
          {payment && (
            <div className="bg-white rounded-lg shadow-md p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Payment</h2>
              <dl className="space-y-3">
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-500">Status</dt>
                  <dd><OrderStatusBadge status={payment.status} /></dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-500">Method</dt>
                  <dd className="text-sm text-gray-900">{payment.paymentMethod}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-500">Amount</dt>
                  <dd className="text-sm text-gray-900">${payment.amount.toFixed(2)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-500">Transaction ID</dt>
                  <dd className="text-sm text-gray-900 font-mono text-xs">{payment.transactionId}</dd>
                </div>
              </dl>
            </div>
          )}

          {notifications.length > 0 && (
            <div className="bg-white rounded-lg shadow-md p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Notifications</h2>
              <div className="space-y-3">
                {notifications.map((n) => (
                  <div key={n.id} className="bg-gray-50 rounded-md p-3">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-medium text-gray-500">{n.type}</span>
                      <span className="text-xs text-gray-400">
                        {new Date(n.sentAt).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm text-gray-700">{n.message}</p>
                    <p className="text-xs text-gray-400 mt-1">To: {n.recipient}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Cross-links */}
          <div className="bg-white rounded-lg shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">What Happens Next</h2>
            <div className="space-y-2 text-sm text-gray-600">
              <p>1. The <strong>restaurant</strong> will accept and prepare your order.</p>
              <p>2. A <strong>courier</strong> will be assigned to deliver it.</p>
              <p>3. Track progress as status updates in real time (auto-refreshes every 15s).</p>
            </div>
            <div className="mt-4 flex gap-3">
              <Link
                href="/restaurant"
                className="text-green-600 text-sm font-medium hover:underline"
              >
                Switch to Restaurant view
              </Link>
              <Link
                href="/courier"
                className="text-blue-600 text-sm font-medium hover:underline"
              >
                Switch to Courier view
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
