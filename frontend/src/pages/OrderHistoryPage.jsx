import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCurrency } from "../currency/CurrencyContext";
import { friendlyErrorMessage } from "../utils/errors";
import StatusBadge from "../components/StatusBadge";
import AppHeader from "../components/AppHeader";
import "./OrderHistoryPage.css";

export default function OrderHistoryPage() {
  const apiFetch = useApiFetch();
  const { formatPrice } = useCurrency();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function loadOrders() {
      try {
        // No user ID here at all -- the backend derives "whose orders" from the caller's
        // own Cognito token (the same X-User-Sub header pattern as placing an order),
        // never from anything the client specifies. This is what actually fixes the
        // hardcoded-Alice gap, rather than just hardcoding a different ID instead.
        const response = await apiFetch("/orders/mine");
        if (!response.ok) {
          // Covers the gateway's circuit-breaker fallback ({"message": "..."}, not an
          // order array) alongside any other non-2xx response -- without this check,
          // that shape would reach setOrders() and crash the list render below on
          // "orders.map is not a function" instead of showing a clear message.
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load order history");
        }
        const data = await response.json();
        if (!cancelled) setOrders(data);
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadOrders();
    return () => {
      cancelled = true;
    };
  }, [apiFetch]);

  if (loading) {
    return (
      <>
        <AppHeader />
        <div className="history-page">
          <p className="text-muted">Loading order history...</p>
        </div>
      </>
    );
  }
  if (error) {
    return (
      <>
        <AppHeader />
        <div className="history-page">
          <p className="text-error">{error}</p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader />
      <div className="history-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <h1>Order history</h1>

        {orders.length === 0 ? (
          <p className="text-muted">No orders yet.</p>
        ) : (
          <ul className="history-list">
            {orders.map((order) => (
              <li className="history-row" key={order.id}>
                <div className="history-row-main">
                  <Link to={`/orders/${order.id}`} className="history-order-link">
                    Order #{order.id}
                  </Link>
                  <StatusBadge status={order.status} />
                </div>
                <div className="history-row-meta text-muted">
                  <span>{formatPrice(order.totalAmount)}</span>
                  <span>{new Date(order.createdAt).toLocaleString()}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
