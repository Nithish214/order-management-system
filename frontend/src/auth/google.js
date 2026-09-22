import { generateCodeChallenge, generateCodeVerifier } from "./pkce";

// sessionStorage, not localStorage -- this verifier is only ever needed once, to finish
// the redirect round trip that's about to start; it has no reason to outlive the tab, the
// way a "remember me" value might.
const VERIFIER_STORAGE_KEY = "google_pkce_verifier";

// Redirects the whole page to Cognito's Hosted UI with identity_provider=Google, which
// hands off to Google's own consent screen, then back to Cognito, then back here (see
// AuthContext's bootstrap effect for the return leg). Not a popup/iframe -- Google's OAuth
// endpoints refuse to render inside one (X-Frame-Options), so a full-page redirect is the
// only option, same as every "Sign in with Google" button anywhere else.
export async function startGoogleLogin() {
  const codeVerifier = generateCodeVerifier();
  sessionStorage.setItem(VERIFIER_STORAGE_KEY, codeVerifier);
  const codeChallenge = await generateCodeChallenge(codeVerifier);

  // Must be the browser's exact origin, no trailing slash/path -- this has to match,
  // character for character, both the "Allowed callback URLs" entry configured on the
  // Cognito app client AND the redirect_uri this same origin sends back in the token
  // exchange (AuthContext's bootstrap effect) -- OAuth2 requires the two to be identical.
  const redirectUri = window.location.origin;

  const params = new URLSearchParams({
    identity_provider: "Google",
    client_id: import.meta.env.VITE_COGNITO_CLIENT_ID,
    response_type: "code",
    scope: "openid email profile",
    redirect_uri: redirectUri,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
  });

  window.location.href = `${import.meta.env.VITE_COGNITO_DOMAIN}/oauth2/authorize?${params}`;
}

// Consumed once, by AuthContext, right after the redirect back from Cognito/Google.
export function takeStoredCodeVerifier() {
  const verifier = sessionStorage.getItem(VERIFIER_STORAGE_KEY);
  sessionStorage.removeItem(VERIFIER_STORAGE_KEY);
  return verifier;
}
