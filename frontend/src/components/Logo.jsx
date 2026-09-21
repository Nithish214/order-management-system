import { CartIcon } from "./icons";
import "./Logo.css";

// The one wordmark, used everywhere the app identifies itself: AppHeader's nav (wrapped
// in a <Link to="/"> by that component) and the two auth pages (plain, not a link --
// you're already looking at the only place those pages go). A visual mark (this cart
// glyph in an accent-filled square) plus styled text, not the literal product name
// rendered as plain unstyled text the way it used to be -- that's the actual difference
// between "a page title" and "a brand."
export default function Logo({ size = "md" }) {
  return (
    <span className={`logo logo--${size}`}>
      <span className="logo-mark" aria-hidden="true">
        <CartIcon size={size === "lg" ? 22 : 16} />
      </span>
      Cartly
    </span>
  );
}
