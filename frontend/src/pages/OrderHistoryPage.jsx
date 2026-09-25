import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCurrency } from "../currency/CurrencyContext";
import { friendlyErrorMessage } from "../utils/errors";
import { formatAddressOneLine } from "../utils/address";
import StatusBadge from "../components/StatusBadge";
import AppHeader from "../components/AppHeader";
import ErrorState from "../components/ErrorState";
import Skeleton from "../components/Skeleton";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./OrderHistoryPage.css";

// A handful of these, not a spinner -- this list always resolves into the same shape (a
// title + a status pill, then a price + date line), predictable enough that showing
// that shape early reads as "this is about to be your order list." A fixed, small count
// -- unlike the real list, there's no known number of orders to guess at ahead of time,
// so this just needs to look like "a few rows," not match the eventual real count.
const SKELETON_ROW_COUNT = 3;

function OrderHistorySkeleton() {
  return (
    <ul className="history-list">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <li className="history-row" key={index}>
          <div className="history-row-main">
            <Skeleton className="skeleton-text skeleton-order-link" />
            <Skeleton className="skeleton-pill" />
          </div>
          <Skeleton className="skeleton-text skeleton-order-meta" />
        </li>
      ))}
    </ul>
  );
}

export default function OrderHistoryPage() {
  useDocumentTitle("Order history");
  const apiFetch = useApiFetch();
  const { formatPrice } = useCurrency();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  // Bumped by the "Try again" button in the error state below -- included in the load
  // effect's dependency array purely as a re-run trigger.
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function loadOrders() {
      setLoading(true);
      setError(null);
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
  }, [apiFetch, retryCount]);

  if (loading) {
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
          <OrderHistorySkeleton />
        </div>
      </>
    );
  }
  if (error) {
    return (
      <>
        <AppHeader />
        <div className="history-page">
          <ErrorState message={error} onRetry={() => setRetryCount((c) => c + 1)} />
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
          <div className="history-empty">
            <p className="text-muted">No orders yet.</p>
            <Link to="/" className="btn-secondary">
              Browse products
            </Link>
          </div>
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
                {/* The snapshot stored on the order, so it stays accurate however the saved
                    address changes later. Absent for orders from before addresses existed. */}
                {order.shippingAddress && (
                  <div className="history-row-ship text-muted">
                    Ships to {formatAddressOneLine(order.shippingAddress)}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
