import { createContext, useCallback, useContext, useState } from "react";
import { login as cognitoLogin } from "./cognito";

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
  // The real tradeoff: refreshing the page logs you out (there's no BFF here to hold a
  // refresh token safely in an httpOnly cookie instead) -- an accepted limitation for
  // a learning project's v1, not something to silently work around with localStorage.
  const [accessToken, setAccessToken] = useState(null);

  // useCallback memoizes the function itself -- without it, AuthProvider re-rendering
  // for any reason would hand out a brand-new `login`/`logout` function reference each
  // time, even though the *behavior* never changed. That matters because other code
  // (useApiFetch, below) puts these in dependency arrays; a function that's "different"
  // on every render, even when nothing meaningful changed, makes anything depending on
  // it re-run constantly. The empty dependency array here is valid because setAccessToken
  // (from useState) is itself guaranteed stable by React across the component's lifetime.
  const login = useCallback(async (email, password) => {
    const result = await cognitoLogin(email, password);
    // We only ever keep the Access token -- see the id-token-vs-access-token note in
    // the write-up. The Gateway's Resource Server config accepts either (both are
    // signed by the same Cognito keys), but Access tokens are the ones meant for APIs,
    // and only they reliably carry cognito:groups the way our restock authorization
    // check depends on.
    setAccessToken(result.AccessToken);

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
        Authorization: `Bearer ${result.AccessToken}`,
      },
      body: JSON.stringify({ email }),
    }).catch(() => {});
  }, []);

  const logout = useCallback(() => {
    setAccessToken(null);
  }, []);

  const value = {
    accessToken,
    isAuthenticated: accessToken !== null,
    login,
    logout,
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
