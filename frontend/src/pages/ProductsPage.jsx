import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { addProductImage } from "../api/productImages";
import { useCart } from "../cart/CartContext";
import CartSummary from "../cart/CartSummary";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import AppHeader from "../components/AppHeader";
import "./ProductsPage.css";

// This bucket also hosts this app's own frontend code -- kept in sync with the exact
// same allowlist Order Service enforces server-side (ProductImageUploadService), so a
// rejected file type shows up immediately as a clear message here instead of only after
// a round trip to the backend.
const ACCEPTED_IMAGE_TYPES = "image/jpeg,image/png,image/webp";

// Kept in sync with ProductImageUploadService.MAX_IMAGES_PER_PRODUCT (server-side) --
// purely so this page can grey out "Add image" instead of letting an admin pick a file
// only to have the upload rejected once it reaches the backend. The backend enforces this
// for real; this is just an earlier, friendlier version of the same rule.
const MAX_IMAGES_PER_PRODUCT = 6;

export default function ProductsPage() {
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { isAdmin } = useAuth();
  const fileInputRef = useRef(null);

  const [products, setProducts] = useState([]);
  // Keyed by productId -- Inventory Service's own data (GET /stock), fetched
  // independently of /products (see the two separate effects below) rather than
  // merged into the product objects, since the two come from two different services
  // and the same product's price/name and its live stock level can each change
  // independently -- and, now, since stock doesn't need refetching every time a search
  // narrows down which products are showing.
  const [stockByProductId, setStockByProductId] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  // What's actually in the search box right now, updated on every keystroke.
  const [searchQuery, setSearchQuery] = useState("");
  // What the product-fetching effect below actually reacts to -- deliberately NOT the
  // same state as searchQuery. Debouncing (see the effect that updates this, further
  // down) means a request only actually fires ~300ms after you stop typing, not on
  // every single keystroke -- long enough to feel instant, short enough that firing a
  // network request per letter typed would be wasteful for both this app and the
  // backend it's calling.
  const [debouncedQuery, setDebouncedQuery] = useState("");
  // Separate from `loading` on purpose: `loading` gates the full-page "Loading
  // products..." replacement below, which should only ever happen once, on the very
  // first load. Every SEARCH after that also needs `setLoading(true)` treatment on the
  // OLD code below -- but reusing `loading` for that would unmount the entire page
  // (search box included) on every single keystroke's debounced re-fetch, which would
  // fight the user trying to keep typing. `searching` drives a much smaller, inline
  // indicator instead (see the product-list section), leaving the search box and the
  // rest of the page untouched while a search is in flight.
  const [searching, setSearching] = useState(false);
  const isFirstProductLoad = useRef(true);
  // Which product the next file the user picks belongs to -- set the instant they click
  // "Upload image" on a specific row, read back once the shared hidden <input> fires its
  // onChange. uploadingId separately drives the per-row "Uploading..." state.
  const [uploadTargetId, setUploadTargetId] = useState(null);
  const [uploadingId, setUploadingId] = useState(null);
  // One typed-but-not-yet-submitted restock value per row, so an admin can have several
  // rows' inputs filled in at once without them interfering with each other.
  const [restockInputs, setRestockInputs] = useState({});
  const [restockingId, setRestockingId] = useState(null);

  // useEffect runs a side effect (anything that reaches outside this component, like a
  // network call) *after* React renders. The dependency array at the end, [], is what
  // tells React "run this exactly once, right after the first render, and never again" --
  // an empty array means "doesn't depend on anything that changes," so there's nothing
  // that would ever cause it to re-run. Compare this to Phase C's polling version later,
  // where a non-empty array or a repeating interval changes that behavior deliberately.
  // Stock: fetched exactly once, on mount -- unlike products (below), it never needs
  // refetching just because a search narrowed down which rows are currently showing.
  useEffect(() => {
    let cancelled = false;

    async function loadStock() {
      try {
        const stockResponse = await apiFetch("/stock");
        if (!stockResponse.ok) {
          const body = await stockResponse.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load stock");
        }
        const stockData = await stockResponse.json();
        if (!cancelled) {
          setStockByProductId(
            Object.fromEntries(stockData.map((stock) => [stock.productId, stock.availableQuantity]))
          );
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyErrorMessage(err));
        }
      }
    }

    loadStock();

    return () => {
      cancelled = true;
    };
  }, [apiFetch]);

  // Debounce: only update debouncedQuery (which the effect below actually reacts to)
  // 300ms after the user stops typing. Every keystroke resets this timer via the
  // cleanup function -- clearTimeout on the PREVIOUS pending timer before starting a
  // new one -- so only the very last keystroke in a burst of typing ever actually
  // survives long enough to fire.
  useEffect(() => {
    const timeoutId = setTimeout(() => setDebouncedQuery(searchQuery), 300);
    return () => clearTimeout(timeoutId);
  }, [searchQuery]);

  // Products: re-fetched whenever debouncedQuery changes -- including its initial ""
  // value on mount, which naturally requests the full catalog (see ProductController's
  // searchProducts: a blank/missing q returns everything, same shape as plain
  // GET /products) rather than needing a separate branch here for "no search yet."
  useEffect(() => {
    let cancelled = false;

    async function loadProducts() {
      // Only the very first load blanks the whole page (see the `if (loading)` early
      // return below) -- every search after that just flips the small inline
      // `searching` indicator instead, so the search box itself never disappears or
      // loses focus while results come back.
      if (isFirstProductLoad.current) {
        setLoading(true);
      } else {
        setSearching(true);
      }
      try {
        const path = debouncedQuery
          ? `/products/search?q=${encodeURIComponent(debouncedQuery)}`
          : "/products";
        const response = await apiFetch(path);
        // The gateway's circuit breaker (api-gateway's FallbackController) can return a
        // real, non-array 503 response when order-service is unhealthy -- without this
        // check, that shape would reach `.map()` below and crash on "not a function"
        // instead of showing the fallback's own clear message.
        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load products");
        }
        const data = await response.json();
        if (!cancelled) {
          setProducts(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
          setSearching(false);
          isFirstProductLoad.current = false;
        }
      }
    }

    loadProducts();

    // The cleanup function: if this component unmounts, or debouncedQuery changes
    // again before this fetch finishes (e.g. clearing the search box right after
    // typing something), this flips `cancelled` so the late-arriving response doesn't
    // call setState on a stale request -- without it, a slow search response for an
    // OLDER query could overwrite a newer, already-displayed result.
    return () => {
      cancelled = true;
    };
  }, [apiFetch, debouncedQuery]);

  // One shared hidden file input for every row, rather than one per product -- a file
  // input has no visual presence of its own anyway, so there's nothing gained by
  // duplicating it, and this way there's exactly one onChange handler to reason about.
  function handleUploadClick(productId) {
    setUploadTargetId(productId);
    fileInputRef.current?.click();
  }

  async function handleFileSelected(event) {
    const file = event.target.files?.[0];
    // Reset immediately so picking the exact same file again still fires onChange next
    // time -- browsers otherwise treat "same file, same input" as no change at all.
    event.target.value = "";
    if (!file || uploadTargetId == null) return;

    const productId = uploadTargetId;
    setUploadingId(productId);
    setError(null);
    try {
      const updated = await addProductImage(apiFetch, productId, file);
      setProducts((prev) => prev.map((product) => (product.id === updated.id ? updated : product)));
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setUploadingId(null);
      setUploadTargetId(null);
    }
  }

  // Restocking ADDS the given quantity to whatever's currently there -- it's "a delivery
  // of N more units arrived," never "set the count to N" (see StockController's comment
  // on why there's deliberately no way to just overwrite a quantity outright).
  async function handleRestock(productId) {
    const quantity = Number(restockInputs[productId]);
    if (!Number.isInteger(quantity) || quantity <= 0) {
      setError("Enter a positive whole number to restock.");
      return;
    }

    setRestockingId(productId);
    setError(null);
    try {
      const response = await apiFetch(`/stock/${productId}/restock`, {
        method: "POST",
        body: JSON.stringify({ quantity }),
      });
      if (!response.ok) {
        throw new Error("Could not restock this product");
      }
      const updated = await response.json();
      setStockByProductId((prev) => ({ ...prev, [productId]: updated.availableQuantity }));
      setRestockInputs((prev) => ({ ...prev, [productId]: "" }));
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setRestockingId(null);
    }
  }

  if (loading) {
    return (
      <>
        <AppHeader />
        <div className="products-page">
          <p className="text-muted">Loading products...</p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader />
      <div className="products-page">
        <h1 className="products-heading">Products</h1>

        {/* Deliberately outside the `loading`/`searching` conditionals -- this input
            must never unmount or lose focus while a search is in flight, which is the
            whole reason `searching` exists as a state separate from `loading` (see the
            product-fetching effect above). */}
        <input
          type="search"
          className="product-search-input"
          placeholder="Search products..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          aria-label="Search products"
        />

        {error && <p className="text-error page-error">{error}</p>}

        {/* Shared by every row -- see handleUploadClick/handleFileSelected. hidden (not a
            display:none style) is the plain HTML way to keep this out of the layout and
            off-screen while still fully usable via fileInputRef.current.click(). */}
        <input
          type="file"
          accept={ACCEPTED_IMAGE_TYPES}
          ref={fileInputRef}
          onChange={handleFileSelected}
          hidden
        />

        <div className="products-layout">
          <section className="product-list">
            {/* Genuinely separate messages, not the same text for both cases: an empty
                catalog and "your search matched nothing" are different situations, and
                telling a searching user "no products available right now" would
                incorrectly suggest the whole catalog is empty rather than just their
                search term. */}
            {searching && <p className="text-muted">Searching...</p>}
            {!searching && products.length === 0 && (
              <p className="text-muted">
                {debouncedQuery ? `No products match "${debouncedQuery}".` : "No products available right now."}
              </p>
            )}
            {!searching && products.map((product) => (
              <div className="product-row" key={product.id}>
                <div className="product-row-main">
                  {/* Only the thumbnail + name/sku navigate to the product's own page --
                      "Add to cart" and the admin upload button below stay independently
                      clickable without triggering that navigation, since they're each
                      their own action, not a link. */}
                  <Link to={`/products/${product.id}`} className="product-row-link">
                    <div className="product-thumb">
                      {product.images.length > 0 ? (
                        <img src={product.images[0].imageUrl} alt={product.name} />
                      ) : (
                        <div className="product-thumb-placeholder" aria-hidden="true" />
                      )}
                    </div>
                    <div className="product-info">
                      <p className="product-name">{product.name}</p>
                      <p className="product-sku text-muted">{product.sku}</p>
                    </div>
                  </Link>
                  <StockCount quantity={stockByProductId[product.id]} />
                  <p className="product-price">${product.unitPrice.toFixed(2)}</p>
                  <button className="btn-secondary" onClick={() => cart.addItem(product)}>
                    Add to cart
                  </button>
                </div>
                {isAdmin && (
                  <div className="product-row-admin">
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => handleUploadClick(product.id)}
                      disabled={uploadingId === product.id || product.images.length >= MAX_IMAGES_PER_PRODUCT}
                    >
                      {uploadingId === product.id
                        ? "Uploading..."
                        : product.images.length >= MAX_IMAGES_PER_PRODUCT
                          ? "Max images reached"
                          : product.images.length > 0
                            ? "Add image"
                            : "Upload image"}
                    </button>
                    <div className="restock-control">
                      <input
                        type="number"
                        min="1"
                        placeholder="Qty"
                        value={restockInputs[product.id] ?? ""}
                        onChange={(e) =>
                          setRestockInputs((prev) => ({ ...prev, [product.id]: e.target.value }))
                        }
                        aria-label={`Quantity to restock for ${product.name}`}
                      />
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => handleRestock(product.id)}
                        disabled={restockingId === product.id}
                      >
                        {restockingId === product.id ? "Restocking..." : "Restock"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </section>

          <aside className="cart-sidebar">
            <CartSummary />
          </aside>
        </div>
      </div>
    </>
  );
}
