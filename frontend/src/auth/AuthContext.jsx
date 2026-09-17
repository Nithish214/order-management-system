import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { bffLogin, bffLogout, bffRefresh } from "./bff";
import { decodeJwtPayload } from "../utils/jwt";

// React Context solves one specific problem: passing data (here, the access token and
// login/logout functions) to components anywhere in the tree without manually threading
// it through every intermediate component's props ("prop drilling"). Any component
// wrapped in <AuthProvider> can call useAuth() and get the current value directly,
// no matter how deeply nested it is.
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // In-memory only -- deliberately not localStorage/sessionStorage. If a malicious
  // script ever got injected into this page (XSS -- e.g. via a compromised dependency,
  // or unescaped user content rendered somewhere), it can read anything in
  // localStorage, including tokens, and exfiltrate them. A plain JS variable inside
  // React state isn't reachable the same way from arbitrary injected scripts in the
  // way storage APIs are, and it disappears the instant the tab closes or reloads.
  const [accessToken, setAccessToken] = useState(null);

  // True only until the silent-refresh check below finishes. Without this, a page
  // reload would render <ProtectedRoute> for one instant with isAuthenticated still
  // false (accessToken starts as null every time, on every mount) and bounce an
  // already-logged-in user to /login before bffRefresh() below has had a chance to
  // find their still-valid session.
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  // Runs once, on first mount. This is the actual fix for "reloading the page logs you
  // out": the refresh token now lives in an HttpOnly cookie the browser already sent
  // along with this request automatically (see bffRefresh's credentials: "include") --
  // there's a real, still-valid session to recover here that simply wasn't visible to
  // any JavaScript, this component included, before the BFF existed.
  //
  // Skipped entirely on /login and /signup -- the two pages that exist specifically for
  // a logged-out visitor. Neither page redirects an already-authenticated visitor
  // anywhere else, so restoring a session while sitting on either one had no actual
  // effect for the user, just an entirely expected 401 in the console on every single
  // visit (no session cookie exists for someone who's there to log in). A plain
  // window.location check, not react-router's useLocation -- this only needs to look at
  // wherever the very first page load landed, and AuthProvider itself is mounted once
  // for the whole app's lifetime, not per-route, so a hook that re-renders on
  // client-side navigation wouldn't change when this effect actually runs anyway.
  //
  // Tradeoff worth knowing: someone who already has a valid session but opens /login
  // directly (an old bookmark, a stale tab) won't get silently logged in anymore --
  // they'll see the login form and need to log in again, rather than the previous
  // behavior of quietly restoring their session even there. Since this app doesn't
  // treat "authenticated" as retroactive that visit only lasts until they either log in
  // again or reload on a different page.
  useEffect(() => {
    const path = window.location.pathname;
    if (path === "/login" || path === "/signup") {
      setIsBootstrapping(false);
      return;
    }
    bffRefresh()
      .then((result) => {
        if (result) setAccessToken(result.accessToken);
      })
      .finally(() => setIsBootstrapping(false));
  }, []);

  // useCallback memoizes the function itself -- without it, AuthProvider re-rendering
  // for any reason would hand out a brand-new `login`/`logout` function reference each
  // time, even though the *behavior* never changed. That matters because other code
  // (useApiFetch) puts these in dependency arrays; a function that's "different" on
  // every render, even when nothing meaningful changed, makes anything depending on it
  // re-run constantly.
  const login = useCallback(async (email, password) => {
    const result = await bffLogin(email, password);
    setAccessToken(result.accessToken);

    // Sync the real email onto app_user right away -- a plain fetch here rather than
    // useApiFetch(), deliberately: useApiFetch() reads the token via useAuth(), but
    // setAccessToken above hasn't actually applied yet at this point in the function
    // (React state updates aren't synchronous), so useApiFetch() would still see the
    // *old* (null) token if called here. We already have the fresh token directly in
    // `result`, so just using it in a plain fetch sidesteps that entirely. Best-effort:
    // a failure here shouldn't block login itself.
    fetch(`${import.meta.env.VITE_GATEWAY_URL}/users/me`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${result.accessToken}`,
      },
      body: JSON.stringify({ email }),
    }).catch(() => {});
  }, []);

  // The one function that actually needs a fresh access token mid-session, without the
  // user doing anything -- useApiFetch calls this when the Gateway says the current
  // token is expired, before falling back to a real logout. Returns the new token
  // directly (not just via state) since the caller needs it immediately, in the same
  // tick, to retry the request that just failed -- state updates aren't available that
  // fast.
  const refreshAccessToken = useCallback(async () => {
    const result = await bffRefresh();
    setAccessToken(result ? result.accessToken : null);
    return result ? result.accessToken : null;
  }, []);

  const logout = useCallback(() => {
    // Best-effort: also tells the Gateway to clear the refresh cookie (and revoke it at
    // Cognito) -- but this tab treats the user as logged out immediately regardless,
    // the instant the in-memory token below is cleared, not once this network call
    // returns.
    bffLogout(accessToken);
    setAccessToken(null);
  }, [accessToken]);

  // Display-only -- decides whether to *show* admin-only controls (the product image
  // upload button), nothing more. The actual enforcement happens server-side, every
  // time, regardless of this value: the Gateway independently re-validates the token's
  // signature and re-reads its cognito:groups claim on every request (see
  // SecurityConfig's hasAuthority("ROLE_admin") rules). A user could, in principle,
  // tamper with their own browser to make this read true -- all that would get them is a
  // visible button that still 403s when clicked, since this check runs again for real on
  // the server that actually matters.
  const isAdmin = useMemo(() => {
    if (!accessToken) return false;
    const payload = decodeJwtPayload(accessToken);
    const groups = payload?.["cognito:groups"];
    return Array.isArray(groups) && groups.includes("admin");
  }, [accessToken]);

  const value = {
    accessToken,
    isAuthenticated: accessToken !== null,
    isBootstrapping,
    isAdmin,
    login,
    logout,
    refreshAccessToken,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// A small custom hook wrapping useContext -- this is the conventional pattern so
// components just call useAuth() instead of importing AuthContext + useContext
// separately everywhere, and so a component used outside <AuthProvider> fails loudly
// instead of silently getting null.
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
