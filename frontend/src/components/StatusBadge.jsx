import "./StatusBadge.css";

// Same four meanings, same colors, as the order status page's pipeline/outcome summary
// (see OrderStatusPage.jsx's getStageStates/OutcomeSummary) -- a returning user who's
// already seen that page should recognize this at a glance, not learn a second color
// language for the same four states. Labels match that page's wording too (sentence case,
// not the raw PENDING/CONFIRMED/REJECTED/CANCELLED enum values).
const STATUS_META = {
  PENDING: { label: "Pending", modifier: "pending" },
  CONFIRMED: { label: "Confirmed", modifier: "success" },
  REJECTED: { label: "Rejected", modifier: "error" },
  CANCELLED: { label: "Cancelled", modifier: "neutral" },
};

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] ?? { label: status, modifier: "neutral" };
  return <span className={`status-badge status-badge--${meta.modifier}`}>{meta.label}</span>;
}
