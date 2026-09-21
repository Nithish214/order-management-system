import { Link } from "react-router-dom";
import "./ErrorState.css";

// One consistent shape for "something went wrong and there's nothing else to show" --
// every full-page failure (a product that failed to load, an order that failed to load,
// order history failing entirely) used to just be a bare paragraph of red text with no
// way to recover except a manual browser reload. onRetry re-runs whatever fetch failed;
// "Back to products" is always offered too, since retrying isn't always the right move
// (a 404 will just fail the exact same way again).
export default function ErrorState({ message, onRetry }) {
  return (
    <div className="error-state">
      <p className="text-error error-state-message">{message}</p>
      <div className="error-state-actions">
        {onRetry && (
          <button type="button" className="btn-secondary" onClick={onRetry}>
            Try again
          </button>
        )}
        <Link to="/" className="btn-secondary">
          Back to products
        </Link>
      </div>
    </div>
  );
}
