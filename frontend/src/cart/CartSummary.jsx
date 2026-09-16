import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCart } from "./CartContext";
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
  const [placingOrder, setPlacingOrder] = useState(false);
  const [error, setError] = useState(null);

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

  return (
    <div className="cart-summary">
      <h2>Cart</h2>

      {error && <p className="text-error page-error">{error}</p>}

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
            <strong>${cart.total.toFixed(2)}</strong>
          </div>
          <button className="btn-primary" onClick={handlePlaceOrder} disabled={placingOrder}>
            {placingOrder ? "Placing order..." : "Place order"}
          </button>
        </>
      )}
    </div>
  );
}
