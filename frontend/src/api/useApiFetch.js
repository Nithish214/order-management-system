import { useCallback } from "react";
import { useAuth } from "../auth/AuthContext";

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL;

// A "hook that returns a function" -- a common React pattern for exactly this situation:
// plain fetch() has no way to reach into React Context (hooks only work inside
// components/other hooks), but we still want every API call to automatically carry the
// *current* access token without every caller having to fetch it from useAuth()
// themselves and remember to attach it. Calling useApiFetch() inside a component gives
// you back a ready-to-use function that already has today's token "baked in" via closure.
//
// useCallback here isn't optional: without it, every render of every component that
// calls useApiFetch() would get a brand-new function reference. Any useEffect elsewhere
// that lists this function as a dependency (Phase B's ProductsPage does exactly that)
// would then re-run on *every* render, not just when the token actually changes --
// for a data-fetching effect, that's an infinite loop (fetch -> state update -> render
// -> "new" function -> effect fires again -> fetch -> ...). [accessToken, logout,
// refreshAccessToken] as the dependency array means a new function is only handed out
// when one of those actually changes (login/logout/refresh), which is the only time the
// old one would be wrong.
export function useApiFetch() {
  const { accessToken, logout, refreshAccessToken } = useAuth();

  return useCallback(
    async function apiFetch(path, options = {}) {
      async function attempt(token) {
        return fetch(`${GATEWAY_URL}${path}`, {
          ...options,
          headers: {
            "Content-Type": "application/json",
            ...options.headers,
            Authorization: `Bearer ${token}`,
          },
        });
      }

      // A transport-level failure (a dropped/truncated connection, DNS hiccup, brief
      // network blip) throws before a response ever comes back -- fetch() surfaces this
      // as a plain TypeError, distinct from a normal HTTP error response (a 4xx/5xx
      // still resolves normally, response.ok is just false). That distinction is exactly
      // what makes a single silent retry safe here: this only fires for "the request
      // never actually completed," never for "the server answered but said no" -- so it
      // can't turn one real error into a duplicate side effect, it can only turn one
      // flaky connection attempt into a second, fresh one before giving up and showing
      // the user anything.
      async function attemptWithRetry(token) {
        try {
          return await attempt(token);
        } catch (err) {
          if (!(err instanceof TypeError)) {
            throw err;
          }
          await new Promise((resolve) => setTimeout(resolve, 400));
          return await attempt(token);
        }
      }

      let response = await attemptWithRetry(accessToken);

      if (response.status === 401) {
        // An access token expiring mid-session is now the *expected* case, not a fatal
        // one -- it only lives about an hour. Try the httpOnly refresh cookie once
        // (silently minting a new access token, no re-login needed) before giving up;
        // this is the entire reason the BFF (AuthContext's refreshAccessToken) exists.
        const newToken = await refreshAccessToken();
        if (newToken) {
          response = await attemptWithRetry(newToken);
        }

        // Still 401 after a fresh token (or there was no cookie to refresh from at
        // all) -- this really is a dead session: log the user out and send them back
        // to /login, same as before.
        if (response.status === 401) {
          logout();
          throw new Error("Session expired -- please log in again");
        }
      }

      return response;
    },
    [accessToken, logout, refreshAccessToken]
  );
}
