// Calls the Gateway's own /auth/* endpoints (AuthController, api-gateway) instead of
// Cognito directly -- unlike cognito.js's signUp/confirmSignUp (no tokens involved,
// still safe to call Cognito straight from the browser), login and refresh both need to
// set/read an HttpOnly cookie holding the refresh token, and only a server can do that.
// This is the "BFF" (Backend-For-Frontend): the Gateway now stands between this app and
// Cognito specifically for that reason.
//
// credentials: "include" is required on every call here -- without it, fetch() never
// sends or accepts cookies on a cross-site request (this app is served from CloudFront,
// the Gateway from a different domain via DuckDNS), and the whole mechanism silently
// does nothing.

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL;

export async function bffLogin(email, password) {
  const response = await fetch(`${GATEWAY_URL}/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    throw new Error(response.status === 401 ? "Incorrect email or password" : "Login failed");
  }
  return response.json(); // { accessToken, expiresIn }
}

// Exchanges the httpOnly refresh cookie (sent automatically -- this code never sees its
// value) for a fresh access token. Returns null rather than throwing when there's no
// valid session to restore (no cookie, or Cognito rejected it) -- that's the expected,
// non-error outcome for a logged-out visitor, not a failure.
//
// The timeout matters specifically for AuthContext's bootstrapping effect, which awaits
// this on every single page load before deciding whether to show the app or redirect to
// /login: without it, a request that genuinely never settles (not an error, just hangs --
// the backend fully unreachable, or a dropped connection with no explicit close) would
// leave that effect waiting forever, and the page would stay blank indefinitely instead
// of ever reaching its own "not logged in, go to /login" fallback.
export async function bffRefresh() {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch(`${GATEWAY_URL}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      signal: controller.signal,
    });
    if (!response.ok) return null;
    return await response.json(); // { accessToken, expiresIn }
  } catch {
    // A timeout (the abort above) and a genuine network failure both land here --
    // either way, there's no session to restore right now, so treat it the same as "no
    // cookie" rather than leaving the caller hanging or throwing an uncaught rejection.
    return null;
  } finally {
    clearTimeout(timeoutId);
  }
}

// Best-effort by design -- see AuthContext's logout(), which clears the in-memory access
// token regardless of whether this call even succeeds.
export async function bffLogout(accessToken) {
  await fetch(`${GATEWAY_URL}/auth/logout`, {
    method: "POST",
    credentials: "include",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  }).catch(() => {});
}
