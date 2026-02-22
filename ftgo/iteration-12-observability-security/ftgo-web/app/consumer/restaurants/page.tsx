"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { restaurantsApi, type Restaurant } from "@/lib/api";
import RestaurantCard from "@/components/RestaurantCard";

export default function RestaurantsPage() {
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

  if (loading) return <div className="text-center py-12 text-gray-500">Loading restaurants...</div>;
  if (error) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Browse Restaurants</h1>
        <Link
          href="/consumer/orders"
          className="bg-orange-500 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-orange-600 transition-colors"
        >
          My Orders
        </Link>
      </div>
      {restaurants.length === 0 ? (
        <p className="text-gray-500">No restaurants available. Make sure restaurant-service is running on port 8081.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {restaurants.map((r) => (
            <RestaurantCard key={r.id} restaurant={r} />
          ))}
        </div>
      )}
    </div>
  );
}
