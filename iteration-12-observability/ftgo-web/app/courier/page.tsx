"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { deliveriesApi, type Courier } from "@/lib/api";

export default function CourierDashboard() {
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    deliveriesApi
      .getCouriers()
      .then(setCouriers)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="text-center py-12 text-gray-500">Loading...</div>;
  if (error) return <div className="bg-red-50 text-red-700 p-4 rounded-lg">{error}</div>;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Courier Dashboard</h1>
      <p className="text-gray-500 mb-6">Select your courier identity to manage deliveries.</p>

      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <h2 className="text-sm font-medium text-gray-500 mb-3">Delivery Workflow</h2>
        <div className="flex items-center space-x-4 text-sm text-gray-600">
          <span className="bg-yellow-100 text-yellow-800 px-2 py-1 rounded">PENDING</span>
          <span>&rarr;</span>
          <span className="bg-blue-100 text-blue-800 px-2 py-1 rounded">ASSIGNED</span>
          <span>&rarr;</span>
          <span className="bg-cyan-100 text-cyan-800 px-2 py-1 rounded">PICKED UP</span>
          <span>&rarr;</span>
          <span className="bg-green-100 text-green-800 px-2 py-1 rounded">DELIVERED</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {couriers.map((c) => (
          <Link
            key={c.id}
            href={`/courier/${c.id}/deliveries`}
            className="block bg-white rounded-lg shadow-md p-5 hover:shadow-lg transition-shadow"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">{c.name}</h3>
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                  c.available ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
                }`}
              >
                {c.available ? "Available" : "Busy"}
              </span>
            </div>
            <p className="text-sm text-gray-500 mt-1">{c.phone}</p>
            <div className="mt-3 text-orange-600 text-sm font-medium">
              View Deliveries &rarr;
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
