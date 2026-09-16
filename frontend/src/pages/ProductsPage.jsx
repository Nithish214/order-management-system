import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { uploadProductImage } from "../api/productImages";
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

export default function ProductsPage() {
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { isAdmin } = useAuth();
  const fileInputRef = useRef(null);

  const [products, setProducts] = useState([]);
  // Keyed by productId -- Inventory Service's own data (GET /stock), fetched alongside
  // /products but kept as a separate map rather than merged into the product objects,
  // since the two come from two different services and the same product's price/name
  // and its live stock level can each change independently.
  const [stockByProductId, setStockByProductId] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
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
  useEffect(() => {
    let cancelled = false;

    async function loadProducts() {
      try {
        // Two independent services, fetched together -- Order Service owns the product
        // catalog, Inventory Service owns live stock. Promise.all so the wait is however
        // long the slower of the two takes, not both added together in sequence.
        const [productsResponse, stockResponse] = await Promise.all([
          apiFetch("/products"),
          apiFetch("/stock"),
        ]);
        const productsData = await productsResponse.json();
        const stockData = await stockResponse.json();
        if (!cancelled) {
          setProducts(productsData);
          setStockByProductId(
            Object.fromEntries(stockData.map((stock) => [stock.productId, stock.availableQuantity]))
          );
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadProducts();

    // The cleanup function: if this component unmounts before the fetch finishes (e.g.
    // you navigate away fast), this flips `cancelled` so the late-arriving response
    // doesn't call setState on a component that's no longer there -- React would warn
    // about that ("can't update state on an unmounted component") without this guard.
    return () => {
      cancelled = true;
    };
  }, [apiFetch]);

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
      const updated = await uploadProductImage(apiFetch, productId, file);
      setProducts((prev) =>
        prev.map((product) => (product.id === updated.id ? { ...product, imageUrl: updated.imageUrl } : product))
      );
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
            {products.map((product) => (
              <div className="product-row" key={product.id}>
                <div className="product-row-main">
                  {/* Only the thumbnail + name/sku navigate to the product's own page --
                      "Add to cart" and the admin upload button below stay independently
                      clickable without triggering that navigation, since they're each
                      their own action, not a link. */}
                  <Link to={`/products/${product.id}`} className="product-row-link">
                    <div className="product-thumb">
                      {product.imageUrl ? (
                        <img src={product.imageUrl} alt={product.name} />
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
                      disabled={uploadingId === product.id}
                    >
                      {uploadingId === product.id
                        ? "Uploading..."
                        : product.imageUrl
                          ? "Change image"
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
