// Backs ReviewController (order-service). Same apiFetch pattern as productImages.js --
// takes the authenticated fetch wrapper from useApiFetch so these calls get the
// Authorization header and refresh-on-401 handling for free.

export async function getReviews(apiFetch, productId) {
  const response = await apiFetch(`/products/${productId}/reviews`);
  if (!response.ok) {
    throw new Error("Could not load reviews");
  }
  return response.json();
}

// { purchased, alreadyReviewed } -- lets the page decide whether to show the "write a
// review" form before the user ever tries, rather than rendering it unconditionally and
// only finding out it should have been hidden once the POST below comes back 403/409.
export async function getReviewEligibility(apiFetch, productId) {
  const response = await apiFetch(`/products/${productId}/reviews/eligibility`);
  if (!response.ok) {
    throw new Error("Could not check review eligibility");
  }
  return response.json();
}

export async function createReview(apiFetch, productId, rating, comment) {
  const response = await apiFetch(`/products/${productId}/reviews`, {
    method: "POST",
    body: JSON.stringify({ rating, comment }),
  });
  if (!response.ok) {
    // Distinct messages for the two real rejection reasons -- both are permanent for this
    // session (the eligibility check already filters out the common case, so reaching
    // either of these means something changed between that check and this submit, e.g.
    // another tab already posted the review), not something worth retrying automatically.
    if (response.status === 409) {
      throw new Error("You've already reviewed this product");
    }
    if (response.status === 403 || response.status === 404) {
      throw new Error("Only verified purchasers can review this product");
    }
    throw new Error("Could not submit your review");
  }
  return response.json();
}
