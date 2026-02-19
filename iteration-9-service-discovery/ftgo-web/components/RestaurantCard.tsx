import Link from "next/link";
import type { Restaurant } from "@/lib/api";

export default function RestaurantCard({ restaurant }: { restaurant: Restaurant }) {
  return (
    <div className="bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow">
      <div className="p-5">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900">{restaurant.name}</h3>
            <p className="text-sm text-gray-500 mt-1">{restaurant.address}</p>
            <p className="text-sm text-gray-500">{restaurant.phone}</p>
          </div>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              restaurant.open ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
            }`}
          >
            {restaurant.open ? "Open" : "Closed"}
          </span>
        </div>
        <div className="mt-4 flex items-center justify-between">
          <span className="text-sm text-gray-500">
            {restaurant.menuItems?.length || 0} menu items
          </span>
          <Link
            href={`/consumer/restaurants/${restaurant.id}/menu`}
            className="inline-flex items-center px-3 py-1.5 border border-orange-500 text-sm font-medium rounded-md text-orange-600 hover:bg-orange-50 transition-colors"
          >
            View Menu
          </Link>
        </div>
      </div>
    </div>
  );
}
