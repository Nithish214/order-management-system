// No PKCE here -- deliberately. It was tried first (the more modern default for a public
// OAuth client), but Cognito rejected the exchange with invalid_grant for every attempt
// specifically on the federated (Google) path, even with a verified byte-for-byte matching
// redirect_uri and a well-formed code_verifier -- a known rough edge where Cognito brokering
// through an external IdP doesn't carry PKCE state through the same way a direct
// Cognito-native login does. Dropping it is a reasonable trade here regardless: the actual
// code exchange happens server-side in api-gateway (see AuthController's own comment),
// never in browser JS, which is exactly the class of interception PKCE exists to guard
// against for a public client in the first place.

// Redirects the whole page to Cognito's Hosted UI with identity_provider=Google, which
// hands off to Google's own consent screen, then back to Cognito, then back here (see
// AuthContext's bootstrap effect for the return leg). Not a popup/iframe -- Google's OAuth
// endpoints refuse to render inside one (X-Frame-Options), so a full-page redirect is the
// only option, same as every "Sign in with Google" button anywhere else.
export function startGoogleLogin() {
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
  });

  window.location.href = `${import.meta.env.VITE_COGNITO_DOMAIN}/oauth2/authorize?${params}`;
}
