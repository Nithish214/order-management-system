// A single PUT/DELETE, not the three-step upload flow productImages.js/productVideos.js
// need -- a Spotify link is just a URL an admin pastes in, never a file this app stores
// or has to clean up anywhere itself.

// Same extraction as profile.js's own errorMessage -- worth it here specifically because
// the realistic failure mode isn't a network/operational one (like a video upload's
// might be), it's "pasted something that isn't a Spotify share link." A @Valid failure
// (see SetProductSpotifyRequest's @Pattern) comes back as a flat { fieldName: "what's
// wrong" } map, NOT { message }, same two-shape split profile.js's own comment explains
// -- checking only body.message here would have silently fallen through to the generic
// string on exactly the failure this richer handling exists for.
async function errorMessage(response, fallback) {
  const body = await response.json().catch(() => null);
  if (body && typeof body.message === "string") return body.message;
  if (body && typeof body === "object") {
    const problems = Object.values(body).filter((value) => typeof value === "string");
    if (problems.length > 0) return problems.join(". ");
  }
  return fallback;
}

export async function setProductSpotify(apiFetch, productId, spotifyUrl) {
  const response = await apiFetch(`/products/${productId}/spotify`, {
    method: "PUT",
    body: JSON.stringify({ spotifyUrl }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, "Could not save this Spotify link"));
  }
  return response.json();
}

export async function deleteProductSpotify(apiFetch, productId) {
  const response = await apiFetch(`/products/${productId}/spotify`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, "Could not remove this Spotify link"));
  }
  return response.json();
}
