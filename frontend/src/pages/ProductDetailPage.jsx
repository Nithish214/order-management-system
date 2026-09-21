import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { addProductImage, deleteProductImage } from "../api/productImages";
import { setProductVideo, deleteProductVideo } from "../api/productVideos";
import { useCart } from "../cart/CartContext";
import { useCurrency } from "../currency/CurrencyContext";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import Spinner from "../components/Spinner";
import AppHeader from "../components/AppHeader";
import ErrorState from "../components/ErrorState";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./ProductDetailPage.css";

// Kept in sync with the same allowlist Order Service enforces server-side
// (ProductImageUploadService) -- identical constant to ProductsPage's.
const ACCEPTED_IMAGE_TYPES = "image/jpeg,image/png,image/webp";

// Kept in sync with ProductImageUploadService.MAX_IMAGES_PER_PRODUCT (server-side) --
// same reasoning as ProductsPage's copy of this constant.
const MAX_IMAGES_PER_PRODUCT = 6;

// Kept in sync with ProductVideoUploadService's own allowlist -- just the two formats
// every modern browser plays natively with a plain <video> tag.
const ACCEPTED_VIDEO_TYPES = "video/mp4,video/webm";

export default function ProductDetailPage() {
  const { id } = useParams();
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { formatPrice } = useCurrency();
  const { isAdmin } = useAuth();
  const fileInputRef = useRef(null);
  const videoFileInputRef = useRef(null);

  const [product, setProduct] = useState(null);
  // Undefined until the /stock fetch resolves, distinct from null/0 -- StockCount treats
  // undefined the same as "don't know yet, say nothing", same reasoning as ProductsPage's
  // stockByProductId map simply not having an entry yet.
  const [stockQuantity, setStockQuantity] = useState(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [deletingImageId, setDeletingImageId] = useState(null);
  const [uploadingVideo, setUploadingVideo] = useState(false);
  const [deletingVideo, setDeletingVideo] = useState(false);
  // Which of this product's images is shown large -- an index rather than an id so it
  // stays meaningful (falls back to the new first image) even right after the currently
  // selected image itself is deleted, see handleDeleteImage.
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [restockInput, setRestockInput] = useState("");
  const [restocking, setRestocking] = useState(false);
  // Bumped by the "Try again" button in the fatal-error state below -- included in the
  // load effect's dependency array purely as a re-run trigger, its actual value is never
  // read for anything.
  const [retryCount, setRetryCount] = useState(0);

  useDocumentTitle(product ? product.name : loading ? "Loading product..." : "Product not found");

  useEffect(() => {
    let cancelled = false;

    async function loadProduct() {
      setLoading(true);
      setError(null);
      try {
        // Two independent services -- Order Service owns the product itself, Inventory
        // Service owns its live stock. A stock lookup failure (e.g. no stock record for
        // some reason) shouldn't block showing the product, so it's handled separately,
        // not folded into the same try/catch as the product fetch.
        const response = await apiFetch(`/products/${id}`);
        if (!response.ok) {
          throw new Error(response.status === 404 ? "This product doesn't exist." : "Could not load this product");
        }
        const data = await response.json();
        if (!cancelled) setProduct(data);

        // Stock levels are admin-only information now -- a regular shopper never sees
        // them (see StockCount's render below), so there's no reason to even fetch them
        // for anyone else. Admins still need this, both to see the current count and
        // because handleRestock below updates it after a restock.
        if (isAdmin) {
          apiFetch(`/stock/${id}`)
            .then((stockResponse) => (stockResponse.ok ? stockResponse.json() : null))
            .then((stockData) => {
              if (!cancelled && stockData) setStockQuantity(stockData.availableQuantity);
            })
            .catch(() => {});
        }
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadProduct();
    return () => {
      cancelled = true;
    };
  }, [id, apiFetch, isAdmin, retryCount]);

  function handleAddToCart() {
    cart.addItem(product, quantity);
    setAdded(true);
  }

  async function handleFileSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    setUploading(true);
    setError(null);
    try {
      const updated = await addProductImage(apiFetch, product.id, file);
      setProduct(updated);
      // Jump to the image that was just added -- images always occupy the first slots of
      // the gallery (see galleryItems below), video (if any) always last, so this index
      // is valid regardless of whether a video exists.
      setSelectedImageIndex(updated.images.length - 1);
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setUploading(false);
    }
  }

  async function handleDeleteImage(imageId) {
    setDeletingImageId(imageId);
    setError(null);
    try {
      const updated = await deleteProductImage(apiFetch, product.id, imageId);
      setProduct(updated);
      // The selected index might now point past the end (deleted the last image) or land
      // on a totally different image than before (deleted one earlier in the list, which
      // shifted everything after it back by one) -- clamping to the new last valid index
      // is simple and always leaves something sensible on screen, rather than a picked
      // index this render finds nothing at. Counts the video too (see galleryItems
      // below), since it occupies the final slot whenever one exists.
      setSelectedImageIndex((prev) =>
        Math.min(prev, Math.max(0, updated.images.length + (updated.videoUrl ? 1 : 0) - 1))
      );
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setDeletingImageId(null);
    }
  }

  async function handleVideoFileSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    setUploadingVideo(true);
    setError(null);
    try {
      const updated = await setProductVideo(apiFetch, product.id, file);
      setProduct(updated);
      // The video always occupies the last gallery slot, right after every image (see
      // galleryItems below) -- jump to it so the upload's result is immediately visible.
      setSelectedImageIndex(updated.images.length);
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setUploadingVideo(false);
    }
  }

  async function handleDeleteVideo() {
    setDeletingVideo(true);
    setError(null);
    try {
      const updated = await deleteProductVideo(apiFetch, product.id);
      setProduct(updated);
      // Same clamping reasoning as handleDeleteImage -- the video (now gone) was the
      // last slot, so this falls back to the new last image, or 0 if there are none.
      setSelectedImageIndex((prev) => Math.min(prev, Math.max(0, updated.images.length - 1)));
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setDeletingVideo(false);
    }
  }

  // Same "adds to whatever's already there" semantics as ProductsPage's restock control --
  // see StockController's comment on why there's no way to just overwrite a quantity.
  async function handleRestock() {
    const addQuantity = Number(restockInput);
    if (!Number.isInteger(addQuantity) || addQuantity <= 0) {
      setError("Enter a positive whole number to restock.");
      return;
    }

    setRestocking(true);
    setError(null);
    try {
      const response = await apiFetch(`/stock/${id}/restock`, {
        method: "POST",
        body: JSON.stringify({ quantity: addQuantity }),
      });
      if (!response.ok) {
        throw new Error("Could not restock this product");
      }
      const updated = await response.json();
      setStockQuantity(updated.availableQuantity);
      setRestockInput("");
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setRestocking(false);
    }
  }

  if (loading) {
    return (
      <>
        <AppHeader />
        <div className="detail-page">
          <p className="text-muted loading-row">
            <Spinner /> Loading product...
          </p>
        </div>
      </>
    );
  }

  // A load failure with nothing to show at all gets the page to itself -- an error
  // sitting inline in a half-empty layout below would be more confusing, not less.
  if (error && !product) {
    return (
      <>
        <AppHeader />
        <div className="detail-page">
          <ErrorState message={error} onRetry={() => setRetryCount((c) => c + 1)} />
        </div>
      </>
    );
  }

  // One combined carousel, images first (oldest-first, same order as before) then the
  // video last, if there is one -- browsing the gallery cycles through both instead of
  // the video always sitting in its own separate block below. selectedImageIndex indexes
  // into THIS list now, not just product.images.
  const galleryItems = [
    ...product.images.map((image) => ({ type: "image", key: image.id, id: image.id, url: image.imageUrl })),
    ...(product.videoUrl ? [{ type: "video", key: "video", url: product.videoUrl }] : []),
  ];
  const selectedItem = galleryItems[selectedImageIndex];
  const removingSelected = selectedItem?.type === "video" ? deletingVideo : deletingImageId === selectedItem?.id;

  return (
    <>
      <AppHeader />
      <div className="detail-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>

        <div className="detail-layout">
        <div className="detail-gallery">
          <div className="detail-image">
            {!selectedItem ? (
              <div className="detail-image-placeholder" aria-hidden="true" />
            ) : selectedItem.type === "video" ? (
              <video src={selectedItem.url} controls />
            ) : (
              <img src={selectedItem.url} alt={product.name} />
            )}
          </div>

          {/* Only worth showing once there's an actual choice to make -- a single-item
              gallery (still the common case) looks exactly like it did before this
              feature existed. */}
          {galleryItems.length > 1 && (
            <div className="detail-thumb-strip">
              {galleryItems.map((item, index) => (
                <button
                  key={item.key}
                  type="button"
                  className={"detail-thumb" + (index === selectedImageIndex ? " detail-thumb-selected" : "")}
                  onClick={() => setSelectedImageIndex(index)}
                  aria-label={item.type === "video" ? "Show video" : `Show image ${index + 1} of ${galleryItems.length}`}
                  aria-current={index === selectedImageIndex}
                >
                  {item.type === "video" ? (
                    <span className="detail-thumb-video" aria-hidden="true">&#9654;</span>
                  ) : (
                    <img src={item.url} alt="" />
                  )}
                </button>
              ))}
            </div>
          )}

          {isAdmin && selectedItem && (
            <div className="detail-image-admin">
              <p className="text-muted detail-image-admin-label">
                {selectedImageIndex + 1} of {galleryItems.length}
              </p>
              <button
                type="button"
                className="btn-secondary"
                onClick={() =>
                  selectedItem.type === "video" ? handleDeleteVideo() : handleDeleteImage(selectedItem.id)
                }
                disabled={deletingImageId !== null || deletingVideo}
              >
                {removingSelected && <Spinner size={14} />}
                {removingSelected
                  ? "Removing..."
                  : selectedItem.type === "video"
                    ? "Remove this video"
                    : "Remove this image"}
              </button>
            </div>
          )}
        </div>

        <div className="detail-info">
          {/* Admin-only, same reasoning and same pattern as stock levels just below --
              a SKU is internal catalog/inventory bookkeeping (also just an unadorned
              alphanumeric code, nothing meaningful to a shopper), not something a
              regular customer needs to see. */}
          {isAdmin && <p className="detail-sku text-muted">{product.sku}</p>}
          <h1 className="detail-name">{product.name}</h1>
          <p className="detail-price">{formatPrice(product.unitPrice)}</p>
          {/* Admin-only -- a regular shopper never sees stock levels at all now, "Out of
              stock" included. A shopper CAN still add an out-of-stock item to their cart
              with no warning here; the actual order still gets rejected at checkout
              time regardless (StockController's reservation logic doesn't trust what
              this page shows either way), so nothing is unsafe, just less proactively
              informative than before for that one case. */}
          {isAdmin && (
            <p className="detail-stock">
              <StockCount quantity={stockQuantity} />
            </p>
          )}

          {error && <p className="text-error page-error">{error}</p>}

          <div className="detail-quantity-row">
            <div className="quantity-stepper">
              <button
                type="button"
                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                aria-label="Decrease quantity"
              >
                &minus;
              </button>
              <input
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
                aria-label="Quantity"
              />
              <button type="button" onClick={() => setQuantity((q) => q + 1)} aria-label="Increase quantity">
                +
              </button>
            </div>
            <button className="btn-primary" onClick={handleAddToCart}>
              Add to cart
            </button>
          </div>

          {added && (
            <p className="text-muted">
              Added to cart. <Link to="/">View cart</Link>
            </p>
          )}

          {isAdmin && (
            <div className="detail-admin">
              <input
                type="file"
                accept={ACCEPTED_IMAGE_TYPES}
                ref={fileInputRef}
                onChange={handleFileSelected}
                hidden
              />
              <button
                type="button"
                className="btn-secondary"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading || product.images.length >= MAX_IMAGES_PER_PRODUCT}
              >
                {uploading && <Spinner size={14} />}
                {uploading
                  ? "Uploading..."
                  : product.images.length >= MAX_IMAGES_PER_PRODUCT
                    ? "Max images reached"
                    : product.images.length > 0
                      ? "Add image"
                      : "Upload image"}
              </button>
              <input
                type="file"
                accept={ACCEPTED_VIDEO_TYPES}
                ref={videoFileInputRef}
                onChange={handleVideoFileSelected}
                hidden
              />
              <button
                type="button"
                className="btn-secondary"
                onClick={() => videoFileInputRef.current?.click()}
                disabled={uploadingVideo}
              >
                {uploadingVideo && <Spinner size={14} />}
                {uploadingVideo ? "Uploading..." : product.videoUrl ? "Replace video" : "Upload video"}
              </button>
              <div className="restock-control">
                <input
                  type="number"
                  min="1"
                  placeholder="Qty"
                  value={restockInput}
                  onChange={(e) => setRestockInput(e.target.value)}
                  aria-label="Quantity to restock"
                />
                <button type="button" className="btn-secondary" onClick={handleRestock} disabled={restocking}>
                  {restocking && <Spinner size={14} />}
                  {restocking ? "Restocking..." : "Restock"}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
      </div>
    </>
  );
}
