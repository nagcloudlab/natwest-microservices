"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { restaurantsApi, ordersApi, type Restaurant, type MenuItem } from "@/lib/api";

export default function MenuPage() {
  const params = useParams();
  const router = useRouter();
  const restaurantId = Number(params.id);

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // Order form fields
  const [consumerName, setConsumerName] = useState("");
  const [consumerContact, setConsumerContact] = useState("");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("CREDIT_CARD");

  useEffect(() => {
    Promise.all([
      restaurantsApi.getById(restaurantId),
      restaurantsApi.getMenu(restaurantId),
    ])
      .then(([rest, items]) => {
        setRestaurant(rest);
        setMenuItems(items);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [restaurantId]);

  const setQty = (itemId: number, qty: number) => {
    setQuantities((prev) => ({ ...prev, [itemId]: Math.max(0, qty) }));
  };

  const selectedItems = menuItems.filter((m) => (quantities[m.id] || 0) > 0);
  const total = selectedItems.reduce((sum, m) => sum + m.price * (quantities[m.id] || 0), 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedItems.length === 0) return;
    setSubmitting(true);
    setError("");
    try {
      const order = await ordersApi.create({
        consumerId: 1,
        consumerName,
        consumerContact,
        restaurantId,
        deliveryAddress,
        paymentMethod,
        items: selectedItems.map((m) => ({
          menuItemId: m.id,
          quantity: quantities[m.id],
        })),
      });
      router.push(`/consumer/orders/${order.id}`);
    } catch (e: any) {
      setError(e.message);
      setSubmitting(false);
    }
  };

  if (loading) return <div className="text-center py-12 text-gray-500">Loading menu...</div>;
  if (error && !restaurant) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-1">{restaurant?.name}</h1>
      <p className="text-gray-500 mb-6">{restaurant?.address}</p>

      {error && <div className="bg-red-50 text-red-700 p-4 rounded-lg mb-4">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Menu Items */}
        <div className="lg:col-span-2">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Menu</h2>
          {menuItems.length === 0 ? (
            <p className="text-gray-500">No menu items available.</p>
          ) : (
            <div className="space-y-3">
              {menuItems.map((item) => (
                <div
                  key={item.id}
                  className="bg-white rounded-lg shadow-sm p-4 flex items-center justify-between"
                >
                  <div>
                    <div className="font-medium text-gray-900">{item.name}</div>
                    <div className="text-sm text-gray-500">{item.description}</div>
                    <div className="text-sm font-semibold text-orange-600 mt-1">
                      ${item.price.toFixed(2)}
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setQty(item.id, (quantities[item.id] || 0) - 1)}
                      className="w-8 h-8 rounded-full bg-gray-200 text-gray-600 hover:bg-gray-300 flex items-center justify-center"
                    >
                      -
                    </button>
                    <span className="w-8 text-center font-medium">{quantities[item.id] || 0}</span>
                    <button
                      onClick={() => setQty(item.id, (quantities[item.id] || 0) + 1)}
                      className="w-8 h-8 rounded-full bg-orange-500 text-white hover:bg-orange-600 flex items-center justify-center"
                    >
                      +
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Order Form */}
        <div>
          <div className="bg-white rounded-lg shadow-md p-6 sticky top-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Place Order</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input
                  type="text"
                  required
                  value={consumerName}
                  onChange={(e) => setConsumerName(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-orange-500 focus:border-orange-500"
                  placeholder="Your name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contact</label>
                <input
                  type="text"
                  required
                  value={consumerContact}
                  onChange={(e) => setConsumerContact(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-orange-500 focus:border-orange-500"
                  placeholder="Phone or email"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Delivery Address</label>
                <input
                  type="text"
                  required
                  value={deliveryAddress}
                  onChange={(e) => setDeliveryAddress(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-orange-500 focus:border-orange-500"
                  placeholder="123 Main St"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Payment Method</label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-orange-500 focus:border-orange-500"
                >
                  <option value="CREDIT_CARD">Credit Card</option>
                  <option value="DEBIT_CARD">Debit Card</option>
                  <option value="CASH">Cash</option>
                </select>
              </div>

              {selectedItems.length > 0 && (
                <div className="border-t pt-4">
                  <h3 className="text-sm font-medium text-gray-700 mb-2">Order Summary</h3>
                  {selectedItems.map((item) => (
                    <div key={item.id} className="flex justify-between text-sm text-gray-600 mb-1">
                      <span>
                        {quantities[item.id]}x {item.name}
                      </span>
                      <span>${(item.price * quantities[item.id]).toFixed(2)}</span>
                    </div>
                  ))}
                  <div className="flex justify-between font-semibold text-gray-900 mt-2 pt-2 border-t">
                    <span>Total</span>
                    <span>${total.toFixed(2)}</span>
                  </div>
                </div>
              )}

              <button
                type="submit"
                disabled={selectedItems.length === 0 || submitting}
                className="w-full bg-orange-500 text-white py-2 px-4 rounded-md font-medium hover:bg-orange-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? "Placing Order..." : `Place Order ($${total.toFixed(2)})`}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
