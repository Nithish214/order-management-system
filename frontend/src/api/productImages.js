// The three-step upload flow backing the image endpoints
// (POST /products/{id}/image-upload-url, POST /products/{id}/images, DELETE
// /products/{id}/images/{imageId}). Takes apiFetch (for calls to our own Gateway, which
// need the Authorization header and the refresh-on-401 handling apiFetch already provides)
// and a plain fetch call in the middle for the actual upload -- that one goes straight to
// S3, never to our own servers, so it deliberately does NOT go through apiFetch (no
// Authorization header would even make sense there; the presigned URL itself is what
// authorizes that request).
//
// Both functions below return the full, updated ProductResponse (images included) rather
// than just the one image that changed -- callers replace their whole local copy of the
// product with it, so they never have to separately reason about "did the count/order of
// images change too."
export async function addProductImage(apiFetch, productId, file) {
  const presignResponse = await apiFetch(`/products/${productId}/image-upload-url`, {
    method: "POST",
    body: JSON.stringify({ contentType: file.type }),
  });
  if (!presignResponse.ok) {
    throw new Error("Could not start the upload");
  }
  const { uploadUrl, imageUrl } = await presignResponse.json();

  const uploadResponse = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type },
    body: file,
  });
  if (!uploadResponse.ok) {
    throw new Error("The upload to storage failed");
  }

  const confirmResponse = await apiFetch(`/products/${productId}/images`, {
    method: "POST",
    body: JSON.stringify({ imageUrl }),
  });
  if (!confirmResponse.ok) {
    throw new Error("Uploaded, but could not save it to this product");
  }
  return confirmResponse.json();
}

export async function deleteProductImage(apiFetch, productId, imageId) {
  const response = await apiFetch(`/products/${productId}/images/${imageId}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error("Could not remove this image");
  }
  return response.json();
}
