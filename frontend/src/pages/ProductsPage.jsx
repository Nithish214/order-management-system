import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCart } from "../cart/CartContext";
import { useAuth } from "../auth/AuthContext";

export default function ProductsPage() {
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [placingOrder, setPlacingOrder] = useState(false);

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
          setError(err.message);
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
      // Phase C builds this page -- for now the route doesn't exist yet, so this will
      // 404 until then. Wiring it up now means Phase C only has to add the page itself.
      navigate(`/orders/${order.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setPlacingOrder(false);
    }
  }

  if (loading) return <p>Loading products...</p>;

  return (
    <div style={{ maxWidth: 700, margin: "40px auto", fontFamily: "sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Products</h1>
        <div>
          <Link to="/orders">Order history</Link>
          <button onClick={logout} style={{ marginLeft: 12 }}>
            Log out
          </button>
        </div>
      </div>

      {error && <p style={{ color: "red" }}>{error}</p>}

      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ textAlign: "left" }}>
            <th>Name</th>
            <th>SKU</th>
            <th>Price</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => (
            <tr key={product.id}>
              <td>{product.name}</td>
              <td>{product.sku}</td>
              <td>${product.unitPrice.toFixed(2)}</td>
              <td>
                <button onClick={() => cart.addItem(product)}>Add to cart</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Cart</h2>
      {cart.items.length === 0 ? (
        <p>Cart is empty.</p>
      ) : (
        <>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "left" }}>
                <th>Name</th>
                <th>Quantity</th>
                <th>Line total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {cart.items.map((item) => (
                <tr key={item.productId}>
                  <td>{item.name}</td>
                  <td>
                    <input
                      type="number"
                      min="1"
                      value={item.quantity}
                      onChange={(e) => cart.setQuantity(item.productId, Number(e.target.value))}
                      style={{ width: 50 }}
                    />
                  </td>
                  <td>${(item.unitPrice * item.quantity).toFixed(2)}</td>
                  <td>
                    <button onClick={() => cart.removeItem(item.productId)}>Remove</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>Total: ${cart.total.toFixed(2)}</strong>
          </p>
          <button onClick={handlePlaceOrder} disabled={placingOrder}>
            {placingOrder ? "Placing order..." : "Place order"}
          </button>
        </>
      )}
    </div>
  );
}
