"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { restaurantsApi, type Restaurant } from "@/lib/api";

export default function RestaurantDashboard() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    restaurantsApi
      .getAll()
      .then(setRestaurants)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="text-center py-12 text-gray-500">Loading...</div>;
  if (error) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Restaurant Dashboard</h1>
      <p className="text-gray-500 mb-6">Select your restaurant to manage kitchen tickets.</p>

      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <h2 className="text-sm font-medium text-gray-500 mb-3">Kitchen Ticket Workflow</h2>
        <div className="flex items-center space-x-4 text-sm text-gray-600">
          <span className="bg-yellow-100 text-yellow-800 px-2 py-1 rounded">CREATED</span>
          <span>&rarr;</span>
          <span className="bg-blue-100 text-blue-800 px-2 py-1 rounded">ACCEPTED</span>
          <span>&rarr;</span>
          <span className="bg-indigo-100 text-indigo-800 px-2 py-1 rounded">PREPARING</span>
          <span>&rarr;</span>
          <span className="bg-green-100 text-green-800 px-2 py-1 rounded">READY FOR PICKUP</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {restaurants.map((r) => (
          <Link
            key={r.id}
            href={`/restaurant/${r.id}/tickets`}
            className="block bg-white rounded-lg shadow-md p-5 hover:shadow-lg transition-shadow"
          >
            <h3 className="text-lg font-semibold text-gray-900">{r.name}</h3>
            <p className="text-sm text-gray-500 mt-1">{r.address}</p>
            <p className="text-sm text-gray-500">{r.phone}</p>
            <div className="mt-3 text-orange-600 text-sm font-medium">
              Manage Tickets &rarr;
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
