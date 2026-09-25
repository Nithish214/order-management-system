// Backs UserController's /users/me and AddressController's /users/me/addresses. Same apiFetch
// pattern as reviews.js -- takes the authenticated fetch wrapper from useApiFetch, so these
// calls get the Authorization header and refresh-on-401 handling for free. "me" everywhere,
// never a user id: the backend derives whose data this is from the caller's own token.

// Turns a failed response into a message worth showing. The backend answers two shapes:
// { message } for most errors (404, 409, ...), and for a rejected form a plain
// { fieldName: "what's wrong" } map from bean validation -- flatten that one into a sentence.
async function errorMessage(response, fallback) {
  const body = await response.json().catch(() => null);
  if (body && typeof body.message === "string") return body.message;
  if (body && typeof body === "object") {
    const problems = Object.values(body).filter((value) => typeof value === "string");
    if (problems.length > 0) return problems.join(". ");
  }
  return fallback;
}

async function json(apiFetch, path, options, fallback) {
  const response = await apiFetch(path, options);
  if (!response.ok) {
    const error = new Error(await errorMessage(response, fallback));
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

// { email, name, phoneNumber }. Throws with status 404 for an identity with no profile row
// yet (nothing has ever created one) -- callers treat that as "empty profile", not a failure.
export function getProfile(apiFetch) {
  return json(apiFetch, "/users/me", undefined, "Could not load your profile");
}

export function updateProfile(apiFetch, { name, phoneNumber }) {
  return json(
    apiFetch,
    "/users/me",
    { method: "PUT", body: JSON.stringify({ name, phoneNumber }) },
    "Could not save your profile"
  );
}

export function getAddresses(apiFetch) {
  return json(apiFetch, "/users/me/addresses", undefined, "Could not load your addresses");
}

export function createAddress(apiFetch, address) {
  return json(
    apiFetch,
    "/users/me/addresses",
    { method: "POST", body: JSON.stringify(address) },
    "Could not save the address"
  );
}

export function updateAddress(apiFetch, id, address) {
  return json(
    apiFetch,
    `/users/me/addresses/${id}`,
    { method: "PUT", body: JSON.stringify(address) },
    "Could not save the address"
  );
}

export function deleteAddress(apiFetch, id) {
  return json(apiFetch, `/users/me/addresses/${id}`, { method: "DELETE" }, "Could not delete the address");
}
