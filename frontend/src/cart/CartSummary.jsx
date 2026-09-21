import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "./CartContext";
import { useCurrency } from "../currency/CurrencyContext";
import Spinner from "../components/Spinner";
import { friendlyErrorMessage } from "../utils/errors";
import "./CartSummary.css";

// Shared by two places: the sidebar on the product list (desktop's "watch it update
// live while shopping" view) and the entire content of the dedicated /cart page --
// reachable from anywhere via the site header, and the more natural place to actually
// check your cart on a narrow screen, rather than scrolling past the whole product list
// first. Owns the checkout flow itself (previously lived in ProductsPage) since both
// contexts need identical "place this order" behavior.
export default function CartSummary() {
  const cart = useCart();
  const apiFetch = useApiFetch();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const { currencyCode, formatPrice } = useCurrency();
  const [placingOrder, setPlacingOrder] = useState(false);
  const [error, setError] = useState(null);

  // Regenerated only when the cart's actual contents change (an item added/removed, a
  // quantity edited) -- deliberately NOT regenerated on every render and NOT on every click
  // of "Place order". That's what makes this correct on both sides of the tradeoff: a
  // network-failure retry of the exact same cart (the user just clicks the button again, or
  // apiFetch itself silently retries once after a 401) reuses this same key, so Order
  // Service recognizes it as the same attempt instead of creating a second order -- while
  // actually changing what's in the cart before retrying gets a fresh key, so a stale
  // success response for the old cart can never come back for a materially different order.
  // A plain content comparison, not a reference one -- useMemo's own dependency comparison
  // is by reference, and a new items array reference gets created on every cart edit
  // regardless of whether the contents actually differ.
  const cartContentsKey = JSON.stringify(cart.items);
  // The memoized value (a random UUID) never reads cartContentsKey -- it's purely a
  // change-trigger to force a fresh UUID exactly when the cart's contents change, same idea
  // as this app's key={status} remount trick elsewhere (StatusBadge), just via useMemo
  // instead of a remount. The lint rule can't tell "recompute because X changed" apart from
  // "recompute using X", so it flags this as unused; it isn't.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const idempotencyKey = useMemo(() => crypto.randomUUID(), [cartContentsKey]);

  async function handlePlaceOrder() {
    setPlacingOrder(true);
    setError(null);
    try {
      const response = await apiFetch("/orders", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
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
        // The gateway's rate limiter (protecting order-service/Kafka from being
        // overwhelmed by a sudden burst -- see api-gateway's application.yml, the
        // order-service-orders-create route) responds with a plain 429 and no body at
        // all, unlike every other error response this app produces -- calling
        // response.json() on that would throw its own confusing "Unexpected end of JSON
        // input" error instead of a real message. Checked first, specifically, rather
        // than folded into the generic branch below.
        if (response.status === 429) {
          throw new Error("Too many orders are being placed right now. Please wait a moment and try again.");
        }
        const body = await response.json();
        throw new Error(body.message || "Failed to place order");
      }

      const order = await response.json();
      cart.clear();
      // Tells the order status page this is the exact moment checkout just succeeded
      // (see its own justPlaced state), so it can show a real "Order placed!" moment
      // instead of arriving looking identical to someone just checking on an order they
      // placed five minutes ago. Router state, not a query param -- it's only meaningful
      // for this one navigation and shouldn't linger in the URL if the page is shared or
      // reloaded.
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setPlacingOrder(false);
    }
  }

  return (
    <div className="cart-summary">
      <h2>Cart</h2>

      {error && <p className="text-error page-error">{error}</p>}

      {cart.items.length === 0 ? (
        <div className="cart-empty">
          <p className="text-muted">Cart is empty.</p>
          <Link to="/" className="btn-secondary">
            Browse products
          </Link>
        </div>
      ) : (
        <>
          <ul className="cart-items">
            {cart.items.map((item) => (
              <li className="cart-item" key={item.productId}>
                <div className="cart-item-info">
                  <p className="cart-item-name">{item.name}</p>
                  <p className="cart-item-line-total">
                    {formatPrice(item.unitPrice * item.quantity)}
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
                      onChange={(e) => cart.setQuantity(item.productId, Number(e.target.value))}
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
                  <button type="button" className="cart-remove" onClick={() => cart.removeItem(item.productId)}>
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <div className="cart-total">
            <span>Total</span>
            <strong>{formatPrice(cart.total)}</strong>
          </div>
          {/* Order Service always charges in USD regardless of what's shown here (see
              CurrencyContext's own comment) -- this is the one place that actually matters
              to say so, right next to the number someone's about to commit to paying. */}
          {currencyCode !== "USD" && (
            <p className="text-muted cart-currency-note">
              Estimated in {currencyCode} -- you'll be charged in USD.
            </p>
          )}
          {/* Same rule Order Service itself enforces (see OrderController's isAdmin check) --
              shown here too, not just left to the 403 that would otherwise come back, so an
              admin browsing the catalog sees why there's no way to check out rather than
              hitting a surprise error only after clicking a button that looked enabled. */}
          {isAdmin ? (
            <p className="text-muted">Admin accounts can't place orders.</p>
          ) : (
            <button className="btn-primary" onClick={handlePlaceOrder} disabled={placingOrder}>
              {placingOrder && <Spinner size={14} />}
              {placingOrder ? "Placing order..." : "Place order"}
            </button>
          )}
        </>
      )}
    </div>
  );
}
