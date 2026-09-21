import { Link } from "react-router-dom";
import CartSummary from "../cart/CartSummary";
import AppHeader from "../components/AppHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./CartPage.css";

// Reachable from anywhere via the site header now, not just as the sidebar on the
// product list -- the more natural place to check your cart on a narrow screen, where
// the sidebar would otherwise mean scrolling past the entire product list first.
export default function CartPage() {
  useDocumentTitle("Your cart");

  return (
    <>
      <AppHeader />
      <div className="cart-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <h1>Your cart</h1>
        <CartSummary />
      </div>
    </>
  );
}
