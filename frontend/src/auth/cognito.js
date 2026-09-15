// Calls Cognito's public API directly via fetch -- this is the exact same HTTP call
// we already proved works with curl/Postman earlier, just from the browser instead.
//
// Why not amazon-cognito-identity-js? That SDK exists mainly to handle SRP
// (Secure Remote Password) authentication, where the password itself never crosses the
// network at all -- a real security upgrade over sending it directly. Our app client
// only has ALLOW_USER_PASSWORD_AUTH enabled (not SRP), so the SDK wouldn't actually
// change anything for us here, just add a dependency for a plain JSON API call we can
// already make ourselves. If you enable SRP on the app client later, revisit this and
// use the SDK -- rolling your own SRP implementation is not worth doing by hand.
//
// login() used to live here too, calling InitiateAuth straight from the browser. It
// moved to auth/bff.js (bffLogin), which calls the Gateway instead -- login is now the
// one place a refresh token gets issued, and only a server can hold that safely in an
// HttpOnly cookie. signUp/confirmSignUp stay here: neither one ever touches a token, so
// calling Cognito directly from the browser was never the problem for them.

const COGNITO_URL = `https://cognito-idp.${import.meta.env.VITE_COGNITO_REGION}.amazonaws.com/`;
const CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID;

async function callCognito(target, body) {
  const response = await fetch(COGNITO_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-amz-json-1.1",
      "X-Amz-Target": `AWSCognitoIdentityProviderService.${target}`,
    },
    body: JSON.stringify(body),
  });

  const data = await response.json();
  if (!response.ok) {
    // Cognito's error shape on failure: { "__type": "NotAuthorizedException", "message": "..." }
    throw new Error(data.message || `${target} failed`);
  }
  return data;
}

// SignUp is a *public* Cognito API -- no admin credentials or existing token needed,
// which is exactly what makes self-service signup possible at all (this is the piece
// that was missing before: previously, creating a Cognito user required admin-create-user
// via the CLI/console, since that's an admin-only API).
export async function signUp(email, password) {
  // { UserSub, UserConfirmed, CodeDeliveryDetails }
  return callCognito("SignUp", {
    ClientId: CLIENT_ID,
    Username: email,
    Password: password,
    UserAttributes: [{ Name: "email", Value: email }],
  });
}

// The account stays UNCONFIRMED (can't log in yet) until this succeeds with the code
// Cognito emailed them -- required because our pool has auto-verified-attributes: email,
// meaning Cognito insists on proving the address is real and reachable before allowing login.
export async function confirmSignUp(email, code) {
  return callCognito("ConfirmSignUp", {
    ClientId: CLIENT_ID,
    Username: email,
    ConfirmationCode: code,
  });
}
