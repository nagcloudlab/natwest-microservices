import { NextResponse } from "next/server";
import { cookies } from "next/headers";

export const dynamic = "force-dynamic";

export async function GET() {
  const cookieStore = cookies();
  const token = cookieStore.get("ftgo_token")?.value;

  if (!token) {
    return NextResponse.json({ authenticated: false }, { status: 401 });
  }

  try {
    const payload = JSON.parse(
      Buffer.from(token.split(".")[1], "base64").toString()
    );

    // Check if token is expired
    if (payload.exp * 1000 < Date.now()) {
      const response = NextResponse.json(
        { authenticated: false, error: "Token expired" },
        { status: 401 }
      );
      response.cookies.set("ftgo_token", "", {
        httpOnly: true,
        secure: false,
        sameSite: "lax",
        path: "/",
        maxAge: 0,
      });
      return response;
    }

    return NextResponse.json({
      authenticated: true,
      username: payload.preferred_username,
      email: payload.email,
      roles: payload.realm_access?.roles || [],
    });
  } catch {
    return NextResponse.json({ authenticated: false }, { status: 401 });
  }
}
