import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";

export default function OrderHistoryPage() {
  const apiFetch = useApiFetch();
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
        const data = await response.json();
        if (!cancelled) setOrders(data);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadOrders();
    return () => {
      cancelled = true;
    };
  }, [apiFetch]);

  if (loading) return <p>Loading order history...</p>;
  if (error) return <p style={{ color: "red" }}>{error}</p>;

  return (
    <div style={{ maxWidth: 600, margin: "40px auto", fontFamily: "sans-serif" }}>
      <p>
        <Link to="/">&larr; Back to products</Link>
      </p>
      <h1>Order History</h1>

      {orders.length === 0 ? (
        <p>No orders yet.</p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ textAlign: "left" }}>
              <th>Order</th>
              <th>Status</th>
              <th>Total</th>
              <th>Placed</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link to={`/orders/${order.id}`}>#{order.id}</Link>
                </td>
                <td>{order.status}</td>
                <td>${order.totalAmount.toFixed(2)}</td>
                <td>{new Date(order.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
