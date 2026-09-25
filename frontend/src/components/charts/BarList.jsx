import { Link } from "react-router-dom";
import "./BarList.css";

// Horizontal bars for "compare a handful of named things" -- horizontal rather than
// vertical because the names (product names especially) are long, and a bar's length is
// easier to compare when every bar starts from the same left edge. Every row carries its
// value as text at the bar's tip, so nothing depends on reading a bar's length or its color:
// the number and the name are always right there.
//
// items: [{ key, label, to, value, valueLabel, secondary, tone }]
//   tone: "accent" (default) | "success" | "pending" | "error" | "neutral" -- only ever used
//   where the color MEANS something (order status); a plain ranking stays in the accent.
export default function BarList({ items }) {
  const max = Math.max(1, ...items.map((item) => item.value));

  return (
    <ul className="barlist">
      {items.map((item) => (
        <li key={item.key} className="barlist-row">
          <div className="barlist-head">
            {item.to ? (
              <Link to={item.to} className="barlist-label">
                {item.label}
              </Link>
            ) : (
              <span className="barlist-label">{item.label}</span>
            )}
            <span className="barlist-value">
              {item.valueLabel}
              {item.secondary && <span className="barlist-secondary text-muted"> {item.secondary}</span>}
            </span>
          </div>
          <div className="barlist-baseline">
            <div
              className={`barlist-fill barlist-fill--${item.tone ?? "accent"}`}
              // A 2px floor so a real-but-tiny value still shows as a mark rather than vanishing.
              style={{ width: `max(2px, ${(item.value / max) * 100}%)` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}
