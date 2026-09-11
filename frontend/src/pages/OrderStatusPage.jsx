import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";

const POLL_INTERVAL_MS = 3000;
const TERMINAL_STATUSES = ["CONFIRMED", "CANCELLED"];

export default function OrderStatusPage() {
  // useParams reads the :id segment out of the current URL (e.g. /orders/10 -> id="10") --
  // this is how a route "addresses" one specific resource; it's just string parsing under
  // the hood, tied to whatever pattern you wrote in <Route path="/orders/:id">.
  const { id } = useParams();
  const apiFetch = useApiFetch();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let intervalId;
    let cancelled = false;

    async function poll() {
      try {
        const response = await apiFetch(`/orders/${id}`);
        const data = await response.json();
        if (cancelled) return;

        setOrder(data);

        if (TERMINAL_STATUSES.includes(data.status)) {
          // The whole point of checking this here: once the order has settled, there's
          // nothing left to learn by asking again. Stopping the interval means this page
          // doesn't keep pinging the Gateway every 3 seconds forever after the answer is
          // already known -- a real order status page should stop working once the
          // question it exists to answer has been answered.
          clearInterval(intervalId);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message);
          clearInterval(intervalId);
        }
      }
    }

    poll(); // fetch immediately -- don't make the user wait a full 3s for the first check
    intervalId = setInterval(poll, POLL_INTERVAL_MS);

    // This cleanup function matters more here than anywhere else so far: without it, if
    // you navigate away from this page (e.g. back to the product list) before the order
    // settles, setInterval keeps firing in the background *forever* -- an invisible,
    // permanent leak that keeps calling setState on a component that no longer exists,
    // and keeps hitting your Gateway every 3 seconds for a page nobody is even looking at.
    // React runs this whenever the component unmounts, or before the effect re-runs.
    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [id, apiFetch]);

  if (error) return <p style={{ color: "red" }}>{error}</p>;
  if (!order) return <p>Loading order...</p>;

  return (
    <div style={{ maxWidth: 500, margin: "40px auto", fontFamily: "sans-serif" }}>
      <p>
        <Link to="/">&larr; Back to products</Link>
      </p>
      <h1>Order #{order.id}</h1>

      <Stepper status={order.status} />

      <table style={{ width: "100%", marginTop: 24, borderCollapse: "collapse" }}>
        <tbody>
          {order.items.map((item) => (
            <tr key={item.productId}>
              <td>Product #{item.productId}</td>
              <td>x{item.quantity}</td>
              <td>${item.lineTotal.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>Total: ${order.totalAmount.toFixed(2)}</strong>
      </p>
    </div>
  );
}

// Deliberately two real outcomes, not three -- see the note above the component list:
// "inventory reserved" and "confirmed" are the same backend event, not sequential steps,
// so a fake middle step here would show something that never actually happens.
function Stepper({ status }) {
  const isCancelled = status === "CANCELLED";
  const isConfirmed = status === "CONFIRMED";

  const step2Label = isCancelled ? "Cancelled" : "Confirmed";
  const step2Color = isCancelled ? "crimson" : isConfirmed ? "green" : "gray";

  return (
    <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
      <StepBox label="Order Placed" done color="green" />
      <span>&rarr;</span>
      <StepBox
        label={status === "PENDING" ? "Processing..." : step2Label}
        done={isConfirmed || isCancelled}
        color={step2Color}
      />
    </div>
  );
}

function StepBox({ label, done, color }) {
  return (
    <div
      style={{
        padding: "8px 16px",
        border: `2px solid ${done ? color : "#ccc"}`,
        borderRadius: 6,
        color: done ? color : "#888",
        fontWeight: done ? "bold" : "normal",
      }}
    >
      {label}
    </div>
  );
}
