// Same three-step direct-to-S3 upload flow as productImages.js (POST
// /products/{id}/video-upload-url, then a plain fetch straight to S3, then confirm), just
// for the one optional product video instead of the image gallery. See that file's own
// comment for why the middle upload step deliberately does NOT go through apiFetch.
export async function setProductVideo(apiFetch, productId, file) {
  const presignResponse = await apiFetch(`/products/${productId}/video-upload-url`, {
    method: "POST",
    body: JSON.stringify({ contentType: file.type }),
  });
  if (!presignResponse.ok) {
    throw new Error("Could not start the upload");
  }
  const { uploadUrl, videoUrl } = await presignResponse.json();

  const uploadResponse = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type },
    body: file,
  });
  if (!uploadResponse.ok) {
    throw new Error("The upload to storage failed");
  }

  // PUT, not POST -- this always replaces whatever video the product had (or sets the
  // first one), same reasoning as the backend's own PUT /products/{id}/video.
  const confirmResponse = await apiFetch(`/products/${productId}/video`, {
    method: "PUT",
    body: JSON.stringify({ videoUrl }),
  });
  if (!confirmResponse.ok) {
    throw new Error("Uploaded, but could not save it to this product");
  }
  return confirmResponse.json();
}

export async function deleteProductVideo(apiFetch, productId) {
  const response = await apiFetch(`/products/${productId}/video`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error("Could not remove this video");
  }
  return response.json();
}
