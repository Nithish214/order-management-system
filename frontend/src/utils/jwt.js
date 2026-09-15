// Reads a JWT's payload without verifying its signature -- that's deliberate, not a
// shortcut: this is only ever used to decide whether to *show* the admin upload control,
// never to actually authorize anything. The real gate is the Gateway's own JWT
// verification (see api-gateway's SecurityConfig), which runs again on every request
// regardless of what this function returns. A forged or tampered token would still just
// get a real 403 from the server the moment it tried to actually do anything.
export function decodeJwtPayload(token) {
  try {
    const payload = token.split(".")[1];
    // JWTs use base64url (- and _ instead of + and /, no padding), not plain base64 --
    // atob() only understands the latter.
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}
