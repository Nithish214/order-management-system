import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { uploadProductImage } from "../api/productImages";
import { useCart } from "../cart/CartContext";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import "./ProductsPage.css";

// This bucket also hosts this app's own frontend code -- kept in sync with the exact
// same allowlist Order Service enforces server-side (ProductImageUploadService), so a
// rejected file type shows up immediately as a clear message here instead of only after
// a round trip to the backend.
const ACCEPTED_IMAGE_TYPES = "image/jpeg,image/png,image/webp";

export default function ProductsPage() {
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [placingOrder, setPlacingOrder] = useState(false);
  // Which product the next file the user picks belongs to -- set the instant they click
  // "Upload image" on a specific row, read back once the shared hidden <input> fires its
  // onChange. uploadingId separately drives the per-row "Uploading..." state.
  const [uploadTargetId, setUploadTargetId] = useState(null);
  const [uploadingId, setUploadingId] = useState(null);

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
        const response = await apiFetch("/products");
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

  async function handlePlaceOrder() {
    setPlacingOrder(true);
    setError(null);
    try {
      const response = await apiFetch("/orders", {
        method: "POST",
        // No userId here -- the Gateway derives who's placing the order from the caller's
        // own Cognito token (see UserIdentityHeaderFilter on the backend), never from
        // anything the client sends. Sending one here now would just be ignored.
        body: JSON.stringify({
          items: cart.items.map((item) => ({
            productId: item.productId,
            quantity: item.quantity,
          })),
        }),
      });

      if (!response.ok) {
        const body = await response.json();
        throw new Error(body.message || "Failed to place order");
      }

      const order = await response.json();
      cart.clear();
      navigate(`/orders/${order.id}`);
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setPlacingOrder(false);
    }
  }

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

  if (loading) {
    return (
      <div className="products-page">
        <p className="text-muted">Loading products...</p>
      </div>
    );
  }

  return (
    <div className="products-page">
      <header className="page-header">
        <h1>Products</h1>
        <nav className="page-nav">
          <Link to="/orders">Order history</Link>
          <button onClick={logout} className="btn-secondary">
            Log out
          </button>
        </nav>
      </header>

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
                </div>
              )}
            </div>
          ))}
        </section>

        <aside className="cart-summary">
          <h2>Cart</h2>
          {cart.items.length === 0 ? (
            <p className="text-muted">Cart is empty.</p>
          ) : (
            <>
              <ul className="cart-items">
                {cart.items.map((item) => (
                  <li className="cart-item" key={item.productId}>
                    <div className="cart-item-info">
                      <p className="cart-item-name">{item.name}</p>
                      <p className="cart-item-line-total">
                        ${(item.unitPrice * item.quantity).toFixed(2)}
                      </p>
                    </div>
                    <div className="cart-item-controls">
                      <div className="quantity-stepper">
                        <button
                          type="button"
                          onClick={() => cart.setQuantity(item.productId, item.quantity - 1)}
                          aria-label={`Decrease quantity of ${item.name}`}
                        >
                          &minus;
                        </button>
                        <input
                          type="number"
                          min="1"
                          value={item.quantity}
                          onChange={(e) =>
                            cart.setQuantity(item.productId, Number(e.target.value))
                          }
                          aria-label={`Quantity of ${item.name}`}
                        />
                        <button
                          type="button"
                          onClick={() => cart.setQuantity(item.productId, item.quantity + 1)}
                          aria-label={`Increase quantity of ${item.name}`}
                        >
                          +
                        </button>
                      </div>
                      <button
                        type="button"
                        className="cart-remove"
                        onClick={() => cart.removeItem(item.productId)}
                      >
                        Remove
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              <div className="cart-total">
                <span>Total</span>
                <strong>${cart.total.toFixed(2)}</strong>
              </div>
              <button className="btn-primary" onClick={handlePlaceOrder} disabled={placingOrder}>
                {placingOrder ? "Placing order..." : "Place order"}
              </button>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
