import { NextResponse } from "next/server";

const KEYCLOAK_URL = process.env.KEYCLOAK_URL || "http://localhost:8180";
const REALM = "ftgo";
const CLIENT_ID = "ftgo-app";

export async function POST(request: Request) {
  const { username, password } = await request.json();

  const tokenRes = await fetch(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "password",
        client_id: CLIENT_ID,
        username,
        password,
      }),
    }
  );

  if (!tokenRes.ok) {
    return NextResponse.json(
      { error: "Invalid credentials" },
      { status: 401 }
    );
  }

  const tokenData = await tokenRes.json();
  const accessToken = tokenData.access_token;

  // Decode JWT payload to extract user info
  const payload = JSON.parse(
    Buffer.from(accessToken.split(".")[1], "base64").toString()
  );

  const response = NextResponse.json({
    username: payload.preferred_username,
    email: payload.email,
    roles: payload.realm_access?.roles || [],
  });

  // Store token in httpOnly cookie
  response.cookies.set("ftgo_token", accessToken, {
    httpOnly: true,
    secure: false, // dev mode
    sameSite: "lax",
    path: "/",
    maxAge: tokenData.expires_in,
  });

  return response;
}
