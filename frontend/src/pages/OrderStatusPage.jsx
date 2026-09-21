import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCurrency } from "../currency/CurrencyContext";
import { friendlyErrorMessage } from "../utils/errors";
import AppHeader from "../components/AppHeader";
import Spinner from "../components/Spinner";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./OrderStatusPage.css";

const POLL_INTERVAL_MS = 3000;
// REJECTED (couldn't be fulfilled -- see the reason shown below, either insufficient
// stock or a declined payment) and CANCELLED (the customer cancelled it themselves) are
// both terminal, but distinct outcomes -- see OrderStatus.java for why they're separate
// statuses instead of one shared "didn't happen" status.
const TERMINAL_STATUSES = ["CONFIRMED", "CANCELLED", "REJECTED"];
const CANCELLABLE_STATUSES = ["PENDING", "CONFIRMED"];

export default function OrderStatusPage() {
  // useParams reads the :id segment out of the current URL (e.g. /orders/10 -> id="10") --
  // this is how a route "addresses" one specific resource; it's just string parsing under
  // the hood, tied to whatever pattern you wrote in <Route path="/orders/:id">.
  const { id } = useParams();
  const apiFetch = useApiFetch();
  const { currencyCode, formatPrice } = useCurrency();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  useDocumentTitle(order ? `Order #${order.id}` : "Order status");

  useEffect(() => {
    let intervalId;
    let cancelled = false;

    async function poll() {
      try {
        const response = await apiFetch(`/orders/${id}`);
        if (!response.ok) {
          // Covers the gateway's circuit-breaker fallback here too -- without this, a
          // {"message": "..."} body would flow straight into setOrder() below, silently
          // showing a broken-looking page (every field undefined) instead of the clear
          // error + stopped polling the existing catch block already provides.
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load order status");
        }
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
          setError(friendlyErrorMessage(err));
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

  // Optimistic + reconciled: flips the button/status immediately on success rather than
  // waiting for the next 3s poll tick to notice, same reasoning as the backend itself
  // setting status eagerly instead of waiting on Inventory Service's confirmation.
  async function handleCancel() {
    setCancelling(true);
    setError(null);
    try {
      const response = await apiFetch(`/orders/${id}/cancel`, { method: "POST" });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || "Could not cancel this order");
      }
      const data = await response.json();
      setOrder(data);
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setCancelling(false);
    }
  }

  if (error) {
    return (
      <>
        <AppHeader />
        <p className="text-error">{error}</p>
      </>
    );
  }
  if (!order) {
    return (
      <>
        <AppHeader />
        <p className="text-muted loading-row">
          <Spinner /> Loading order...
        </p>
      </>
    );
  }

  return (
    <>
      <AppHeader />
      <div className="status-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <h1>Order #{order.id}</h1>

        <Stepper status={order.status} />
        {/* key={order.status}: forces a fresh element (and a fresh play of its entrance
            animation) only when the status actually changes, not on every 3s poll tick
            that comes back with the same status. */}
        <OutcomeSummary
          key={order.status}
          status={order.status}
          rejectionReason={order.rejectionReason}
        />

        {CANCELLABLE_STATUSES.includes(order.status) && (
          <button onClick={handleCancel} disabled={cancelling} className="btn-secondary cancel-button">
            {cancelling && <Spinner size={14} />}
            {cancelling ? "Cancelling..." : "Cancel order"}
          </button>
        )}

        <ul className="order-items">
          {order.items.map((item) => (
            <li className="order-item" key={item.productId}>
              {/* Links to the product's own page -- same "thumbnail + name are one
                  clickable unit" pattern as the product grid card, price stays outside
                  the link since it's this ORDER's price at purchase time (see
                  OrderResponse's own comment on why it's snapshotted, not live),
                  not something clicking through to today's product page would explain. */}
              <Link to={`/products/${item.productId}`} className="order-item-info">
                <span className="order-item-thumb">
                  {item.imageUrl ? (
                    <img src={item.imageUrl} alt={item.productName} />
                  ) : (
                    <span className="order-item-thumb-placeholder" aria-hidden="true" />
                  )}
                </span>
                <span className="order-item-name">
                  {item.productName} &times; {item.quantity}
                </span>
              </Link>
              <span>{formatPrice(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
        <div className="order-total">
          <span>Total</span>
          <strong>{formatPrice(order.totalAmount)}</strong>
        </div>
        {currencyCode !== "USD" && (
          <p className="text-muted">Estimated in {currencyCode} -- charged in USD.</p>
        )}
      </div>
    </>
  );
}

const PIPELINE_STAGES = [
  { key: "placed", label: "Order placed" },
  { key: "inventory", label: "Inventory checked" },
  { key: "payment", label: "Payment processed" },
  { key: "confirmed", label: "Confirmed" },
];

// Stages 2 and 3 (inventory + payment) always share the exact same visual state as each
// other, and that's deliberate, not an oversight: GET /orders/:id only ever returns one
// overall status (PENDING/CONFIRMED/REJECTED/CANCELLED) -- never which of those two
// specific steps has individually finished. Showing them progressing one after another
// on a timer would mean inventing timing information this page doesn't actually have,
// which is exactly the decorative-not-honest progress bar the design direction warns
// against. What's always true regardless: by the time status is CONFIRMED, both steps
// genuinely did succeed; if REJECTED, something in that pair genuinely is what stopped
// it -- the specific real reason (which one, and why) is shown in the outcome summary
// below, not guessed at here.
function getStageStates(status) {
  switch (status) {
    case "PENDING":
      return ["done", "active", "active", "upcoming"];
    case "CONFIRMED":
      return ["done", "done", "done", "success"];
    case "REJECTED":
      return ["done", "stopped", "stopped", "upcoming"];
    case "CANCELLED":
    default:
      return ["done", "upcoming", "upcoming", "upcoming"];
  }
}

function Stepper({ status }) {
  const states = getStageStates(status);

  return (
    <ol className="pipeline">
      {PIPELINE_STAGES.map((stage, index) => (
        <li key={stage.key} className={`pipeline-stage pipeline-stage--${states[index]}`}>
          <div className="pipeline-track">
            <span className="pipeline-node" aria-hidden="true">
              {states[index] === "done" || states[index] === "success" ? "✓" : null}
              {states[index] === "stopped" ? "✕" : null}
            </span>
          </div>
          <span className="pipeline-label">{stage.label}</span>
        </li>
      ))}
    </ol>
  );
}

// The plain-language explanation the design direction asks for, kept separate from the
// pipeline itself -- the pipeline shows *where* things stand, this says *what it means*
// in a sentence, using the real reason from the backend (order.rejectionReason) rather
// than a generic "something went wrong."
function OutcomeSummary({ status, rejectionReason }) {
  if (status === "PENDING") {
    return (
      <div className="outcome outcome--pending">
        <p className="outcome-title">Processing your order</p>
        <p className="outcome-detail text-muted">
          Checking stock and payment now -- this usually only takes a few seconds.
        </p>
      </div>
    );
  }

  if (status === "CONFIRMED") {
    return (
      <div className="outcome outcome--success">
        <p className="outcome-title">Your order is confirmed</p>
      </div>
    );
  }

  if (status === "REJECTED") {
    return (
      <div className="outcome outcome--error">
        <p className="outcome-title">Your order was rejected</p>
        {rejectionReason && <p className="outcome-detail">{rejectionReason}</p>}
      </div>
    );
  }

  if (status === "CANCELLED") {
    return (
      <div className="outcome outcome--neutral">
        <p className="outcome-title">You cancelled this order</p>
      </div>
    );
  }

  return null;
}
