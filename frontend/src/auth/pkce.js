// PKCE (RFC 7636) for the Google sign-in redirect -- see google.js and CognitoAuthClient's
// own comment on exchangeAuthorizationCode for the full picture of why this exists. Uses
// the Web Crypto API (crypto.getRandomValues/crypto.subtle), available in every modern
// browser over HTTPS or localhost -- both true here (CloudFront in prod, localhost in dev).

function base64UrlEncode(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  // Standard base64 uses +, /, and = padding -- all three are meaningful characters in a
  // URL query string, so PKCE's own spec (RFC 7636) calls for the URL-safe variant
  // instead: +/  ->  -_, and the padding dropped entirely (it's recoverable from length).
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// 32 random bytes, same size the RFC's own examples use -- the "secret" half of the pair,
// kept in sessionStorage across the redirect to Google and back, never sent anywhere until
// the final token exchange (see AuthContext's bootstrap effect).
export function generateCodeVerifier() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes.buffer);
}

// The "public" half -- a SHA-256 hash of the verifier, safe to send in the browser's own
// redirect URL (to Cognito, then Google) precisely because it's one-way: Cognito can check
// a later-presented verifier against this hash, but nothing here reveals the verifier
// itself to anyone who only sees this redirect.
export async function generateCodeChallenge(codeVerifier) {
  const data = new TextEncoder().encode(codeVerifier);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(digest);
}
