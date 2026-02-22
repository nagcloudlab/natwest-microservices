"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const links = [
  { href: "/", label: "Home" },
  { href: "/consumer/restaurants", label: "Consumer" },
  { href: "/restaurant", label: "Restaurant" },
  { href: "/courier", label: "Courier" },
];

interface UserInfo {
  username: string;
  roles: string[];
}

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<UserInfo | null>(null);

  useEffect(() => {
    fetch("/api/auth/me")
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data?.authenticated) {
          setUser({ username: data.username, roles: data.roles });
        } else {
          setUser(null);
        }
      })
      .catch(() => setUser(null));
  }, [pathname]);

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    setUser(null);
    router.push("/login");
    router.refresh();
  }

  return (
    <nav className="bg-gray-900 text-white shadow-lg">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link href="/" className="text-xl font-bold tracking-tight text-orange-400">
            FTGO
          </Link>
          <div className="flex items-center space-x-1">
            {links.map((link) => {
              const active =
                link.href === "/"
                  ? pathname === "/"
                  : pathname.startsWith(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    active
                      ? "bg-orange-500 text-white"
                      : "text-gray-300 hover:bg-gray-700 hover:text-white"
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}

            <div className="ml-4 pl-4 border-l border-gray-700 flex items-center space-x-3">
              {user ? (
                <>
                  <span className="text-sm text-gray-300">
                    <span className="text-orange-400 font-medium">{user.username}</span>
                  </span>
                  <button
                    onClick={handleLogout}
                    className="px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-md transition-colors"
                  >
                    Logout
                  </button>
                </>
              ) : (
                <Link
                  href="/login"
                  className="px-3 py-1.5 text-xs bg-orange-500 hover:bg-orange-600 rounded-md transition-colors font-medium"
                >
                  Login
                </Link>
              )}
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
}
