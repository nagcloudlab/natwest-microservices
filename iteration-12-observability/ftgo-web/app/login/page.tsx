"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

const presetUsers = [
  { username: "admin", password: "admin123", label: "Admin (all roles)" },
  { username: "consumer", password: "password", label: "Consumer" },
  { username: "restaurant", password: "password", label: "Restaurant" },
  { username: "courier", password: "password", label: "Courier" },
];

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });

      if (!res.ok) {
        setError("Invalid username or password");
        return;
      }

      router.push("/");
      router.refresh();
    } catch {
      setError("Login failed. Is Keycloak running?");
    } finally {
      setLoading(false);
    }
  }

  function fillCredentials(user: string, pass: string) {
    setUsername(user);
    setPassword(pass);
    setError("");
  }

  return (
    <div className="min-h-[70vh] flex items-center justify-center">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-xl shadow-lg p-8">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-gray-900">FTGO Login</h1>
            <p className="text-sm text-gray-500 mt-2">
              Authenticate via Keycloak to access the platform
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none"
                required
              />
            </div>

            {error && (
              <div className="text-red-600 text-sm bg-red-50 p-3 rounded-lg">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 px-4 bg-orange-500 text-white font-medium rounded-lg hover:bg-orange-600 transition-colors disabled:opacity-50"
            >
              {loading ? "Signing in..." : "Sign In"}
            </button>
          </form>

          <div className="mt-8 border-t pt-6">
            <p className="text-xs text-gray-500 mb-3 text-center">
              Quick login — click to fill credentials:
            </p>
            <div className="grid grid-cols-2 gap-2">
              {presetUsers.map((u) => (
                <button
                  key={u.username}
                  onClick={() => fillCredentials(u.username, u.password)}
                  className="px-3 py-2 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-gray-700"
                >
                  {u.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
