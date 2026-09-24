import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { addProductImage, deleteProductImage } from "../api/productImages";
import { setProductVideo, deleteProductVideo } from "../api/productVideos";
import { getReviews, getReviewEligibility, createReview } from "../api/reviews";
import { useCart } from "../cart/CartContext";
import { useCurrency } from "../currency/CurrencyContext";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../toast/ToastContext";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import StarRating from "../components/StarRating";
import Spinner from "../components/Spinner";
import AppHeader from "../components/AppHeader";
import ErrorState from "../components/ErrorState";
import Breadcrumbs from "../components/Breadcrumbs";
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

// 3-4 reads as a real "row," not a single lonely card or an overwhelming full grid --
// see loadRelated below for why this is also the page size requested from the server.
const RELATED_PRODUCT_COUNT = 4;

export default function ProductDetailPage() {
  const { id } = useParams();
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { formatPrice } = useCurrency();
  const { isAdmin } = useAuth();
  const { showToast } = useToast();
  const fileInputRef = useRef(null);
  const videoFileInputRef = useRef(null);

  const [product, setProduct] = useState(null);
  // Separate from `product`/`loading` on purpose -- a slow or failed related-products
  // fetch should never block or fail the actual product page around it. Starts empty,
  // not undefined, so "still loading" and "genuinely none" both just render nothing
  // until this fills in -- see the render below, which only shows the section once it
  // has something to show.
  const [relatedProducts, setRelatedProducts] = useState([]);
  // Same independence reasoning as relatedProducts above -- reviews are a below-the-fold
  // addition to a page whose real job is showing the product itself.
  const [reviews, setReviews] = useState([]);
  // null until the eligibility check resolves -- distinct from {purchased: false, ...},
  // which is a real, known answer. Used to hold off rendering the write-a-review form
  // either way until there's an actual answer, rather than flashing it and then hiding it.
  const [reviewEligibility, setReviewEligibility] = useState(null);
  const [reviewRating, setReviewRating] = useState(0);
  const [reviewComment, setReviewComment] = useState("");
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewError, setReviewError] = useState(null);
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

  // A separate effect, not folded into loadProduct above, specifically so a slow or
  // failed related-products call can never delay or break showing the actual product --
  // this page's real job. Depends on product?.id/product?.category (stable primitives),
  // not the whole `product` object -- an admin uploading an image or restocking replaces
  // `product` with a new object reference every time, which would otherwise re-fetch
  // "related products" on every single one of those unrelated actions.
  useEffect(() => {
    if (!product) return;
    let cancelled = false;

    async function loadRelated() {
      try {
        // Reuses GET /products (the same endpoint the product grid itself calls), no
        // new backend work -- one extra result requested beyond RELATED_PRODUCT_COUNT,
        // since this product itself is always among its own category's results and
        // gets filtered out below; asking for one extra means a full row still shows
        // up rather than sometimes one card short.
        const params = new URLSearchParams({
          category: product.category,
          size: String(RELATED_PRODUCT_COUNT + 1),
        });
        const response = await apiFetch(`/products?${params}`);
        if (!response.ok) return;
        const data = await response.json();
        if (!cancelled) {
          setRelatedProducts(
            data.content.filter((p) => p.id !== product.id).slice(0, RELATED_PRODUCT_COUNT)
          );
        }
      } catch {
        // Best-effort, same reasoning as ProductsPage's own category-sidebar fetch --
        // a "You might also like" row failing to load isn't worth an error message on
        // a page whose actual job (showing this one product) already succeeded.
      }
    }

    loadRelated();
    return () => {
      cancelled = true;
    };
    // Deliberately product?.id/product?.category, not `product` itself -- the lint rule
    // can't tell "re-run because the category changed" apart from "re-run because this
    // object reference changed for any reason at all," and here it's specifically the
    // latter (an admin's image upload, a restock) that this effect must NOT react to --
    // see this effect's own opening comment.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiFetch, product?.id, product?.category]);

  // Another independent effect, same reasoning as related products above -- a slow or
  // failed reviews fetch shouldn't block or break the page around it. isAdmin excluded
  // from eligibility fetching entirely: admin accounts can never place an order at all
  // (see OrderController's own "Admin accounts cannot place orders" rule), so they could
  // never legitimately pass the purchased check either -- skipping the call for them
  // avoids a request that's guaranteed to come back {purchased: false}.
  useEffect(() => {
    if (!product) return;
    let cancelled = false;

    getReviews(apiFetch, product.id)
      .then((data) => {
        if (!cancelled) setReviews(data);
      })
      .catch(() => {});

    if (!isAdmin) {
      getReviewEligibility(apiFetch, product.id)
        .then((data) => {
          if (!cancelled) setReviewEligibility(data);
        })
        .catch(() => {});
    }

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiFetch, product?.id, isAdmin]);

  async function handleSubmitReview(event) {
    event.preventDefault();
    if (reviewRating === 0) {
      setReviewError("Pick a star rating first.");
      return;
    }
    setSubmittingReview(true);
    setReviewError(null);
    try {
      const created = await createReview(apiFetch, product.id, reviewRating, reviewComment.trim() || null);
      // Newest first, matching the order the backend already returns the list in --
      // prepending here means the just-submitted review appears immediately without
      // waiting on (or duplicating) a full re-fetch of the whole list.
      setReviews((prev) => [created, ...prev]);
      setReviewEligibility((prev) => ({ ...prev, alreadyReviewed: true }));
      setReviewRating(0);
      setReviewComment("");
      showToast("Review submitted. Thanks for the feedback!");
    } catch (err) {
      setReviewError(friendlyErrorMessage(err));
    } finally {
      setSubmittingReview(false);
    }
  }

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
      // The one genuinely silent success left in this file after the toast pass --
      // image/video upload and removal all show their own obvious result (a new
      // thumbnail appears, a slide disappears), but this only changes a plain number
      // sitting among several other page elements, easy to miss if it's not glanced at
      // right when it updates.
      showToast(`Restocked ${addQuantity} -- now ${updated.availableQuantity} in stock`);
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
        <Breadcrumbs
          segments={[
            { label: "Home", to: "/" },
            { label: product.category, to: `/?category=${encodeURIComponent(product.category)}` },
            { label: product.name },
          ]}
        />

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
          {/* Only once there's at least one real review -- a 0-star/"(0 reviews)" summary
              on a product nobody's rated yet reads as a bad rating, not an absent one. */}
          {product.reviewCount > 0 && (
            <p className="detail-rating-summary">
              <StarRating rating={product.averageRating} />
              <span className="text-muted">
                {product.averageRating.toFixed(1)} ({product.reviewCount} review{product.reviewCount === 1 ? "" : "s"})
              </span>
            </p>
          )}
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

          {/* The right column's real content -- absent only for the 300 bulk-generated
              products (see Product entity's own comment), which render neither section
              rather than an empty "Description" heading or a specs table with zero rows. */}
          {product.description && (
            <div className="detail-description">
              <h2>Description</h2>
              <p>{product.description}</p>
            </div>
          )}

          {Object.keys(product.specs ?? {}).length > 0 && (
            <div className="detail-specs">
              <h2>Specifications</h2>
              {/* A dl/dt/dd list, not a <table> -- this is name/value pairs, not tabular
                  data with meaningful columns, and specs is a Map so insertion order
                  (the order each migration's JSON was written in, e.g. Author before
                  Format for a book) is what decides display order here, same as
                  product.images relies on query order for which image is the cover. */}
              <dl className="detail-specs-list">
                {Object.entries(product.specs).map(([key, value]) => (
                  <div className="detail-specs-row" key={key}>
                    <dt>{key}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
            </div>
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

        {/* Outside detail-layout, same reasoning as related-products below -- a full-width
            section, not squeezed into the narrower right column, since a real review list
            (star + name + comment + date, per review) needs more room than that column
            has to offer. */}
        <div className="detail-reviews">
          <h2>Reviews</h2>

          {/* Only rendered once eligibility actually resolves (see reviewEligibility's own
              comment) -- purchased-but-not-yet-reviewed is the one state that gets the
              form; every other combination (not purchased, already reviewed, still
              loading) shows nothing here rather than a form that would just reject the
              submission. */}
          {reviewEligibility?.purchased && !reviewEligibility.alreadyReviewed && (
            <form className="review-form" onSubmit={handleSubmitReview}>
              <p className="review-form-label">Write a review</p>
              <StarRating rating={reviewRating} onChange={setReviewRating} size={24} />
              <textarea
                className="review-form-comment"
                placeholder="What did you think? (optional)"
                value={reviewComment}
                onChange={(e) => setReviewComment(e.target.value)}
                rows={3}
              />
              {reviewError && <p className="text-error">{reviewError}</p>}
              <button type="submit" className="btn-primary" disabled={submittingReview}>
                {submittingReview && <Spinner size={14} />}
                {submittingReview ? "Submitting..." : "Submit review"}
              </button>
            </form>
          )}

          {reviews.length === 0 ? (
            <p className="text-muted">No reviews yet.</p>
          ) : (
            <ul className="review-list">
              {reviews.map((review) => (
                <li key={review.id} className="review-list-item">
                  <div className="review-list-header">
                    <StarRating rating={review.rating} size={14} />
                    <span className="review-list-author">{review.reviewerName}</span>
                    <span className="text-muted review-list-date">
                      {new Date(review.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                  {review.comment && <p className="review-list-comment">{review.comment}</p>}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Outside detail-layout on purpose -- this is a full-width row below the
            two-column area, not a third column squeezed into it. Empty until the
            related-products effect above resolves (or if it comes back with nothing,
            e.g. a single-product category), so there's no empty heading in the
            meantime. */}
        {relatedProducts.length > 0 && (
          <div className="related-products">
            <h2>You might also like</h2>
            <div className="related-products-grid">
              {relatedProducts.map((related) => (
                <Link key={related.id} to={`/products/${related.id}`} className="related-product-card">
                  <div className="related-product-image">
                    {related.images?.[0] ? (
                      <img src={related.images[0].imageUrl} alt={related.name} />
                    ) : (
                      <div className="related-product-image-placeholder" aria-hidden="true" />
                    )}
                  </div>
                  <p className="related-product-name">{related.name}</p>
                  <p className="related-product-price">{formatPrice(related.unitPrice)}</p>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </>
  );
}
