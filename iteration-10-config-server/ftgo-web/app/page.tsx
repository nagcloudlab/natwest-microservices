import Link from "next/link";

const roles = [
  {
    title: "Consumer",
    description: "Browse restaurants, view menus, and place food orders.",
    href: "/consumer/restaurants",
    color: "bg-orange-500",
    icon: "\uD83D\uDED2",
  },
  {
    title: "Restaurant",
    description: "Manage kitchen tickets: accept, prepare, and mark orders ready.",
    href: "/restaurant",
    color: "bg-green-500",
    icon: "\uD83C\uDF73",
  },
  {
    title: "Courier",
    description: "View and manage deliveries: assign, pick up, and deliver orders.",
    href: "/courier",
    color: "bg-blue-500",
    icon: "\uD83D\uDE9A",
  },
];

export default function HomePage() {
  return (
    <div>
      <div className="text-center mb-12">
        <h1 className="text-4xl font-bold text-gray-900 mb-4">
          Food To Go
        </h1>
        <p className="text-lg text-gray-600 max-w-2xl mx-auto">
          A microservices training platform. Choose a role to get started.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-4xl mx-auto">
        {roles.map((role) => (
          <Link
            key={role.title}
            href={role.href}
            className="group block bg-white rounded-xl shadow-md overflow-hidden hover:shadow-xl transition-all hover:-translate-y-1"
          >
            <div className={`${role.color} h-2`} />
            <div className="p-6">
              <div className="text-4xl mb-4">{role.icon}</div>
              <h2 className="text-xl font-semibold text-gray-900 group-hover:text-orange-600 transition-colors">
                {role.title}
              </h2>
              <p className="mt-2 text-sm text-gray-500">{role.description}</p>
            </div>
          </Link>
        ))}
      </div>

      <div className="mt-16 bg-white rounded-xl shadow-md p-8 max-w-4xl mx-auto">
        <h2 className="text-2xl font-bold text-gray-900 mb-4">Architecture</h2>
        <p className="text-gray-600 mb-6">
          This app is a <strong>React/Next.js SPA</strong> that communicates with 6 backend microservices
          through <strong>BFF (Backend For Frontend) API routes</strong>.
        </p>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          {[
            { name: "Order Service", port: 8080, desc: "Order lifecycle" },
            { name: "Restaurant Service", port: 8081, desc: "Menus & restaurants" },
            { name: "Notification Service", port: 8082, desc: "Kafka notifications" },
            { name: "Accounting Service", port: 8083, desc: "Payment auth" },
            { name: "Kitchen Service", port: 8084, desc: "Ticket management" },
            { name: "Delivery Service", port: 8085, desc: "Courier & delivery" },
          ].map((svc) => (
            <div key={svc.name} className="bg-gray-50 rounded-lg p-4 border border-gray-200">
              <div className="font-medium text-gray-900 text-sm">{svc.name}</div>
              <div className="text-xs text-gray-500 mt-1">Port {svc.port}</div>
              <div className="text-xs text-gray-400 mt-0.5">{svc.desc}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
