import { Link } from "react-router-dom";
import { useWishlist } from "../wishlist/WishlistContext";
import { useCart } from "../cart/CartContext";
import { useCurrency } from "../currency/CurrencyContext";
import AppHeader from "../components/AppHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./WishlistPage.css";

// Reachable from the site header, same "its own page, not just a sidebar" treatment as
// /cart -- a saved list is exactly the kind of thing someone comes back to on its own,
// not only while mid-shop.
export default function WishlistPage() {
  useDocumentTitle("Your saved items");
  const wishlist = useWishlist();
  const cart = useCart();
  const { formatPrice } = useCurrency();

  // Moves one item to the cart and off the wishlist -- the natural "I've decided on
  // this one" action. Two existing calls (CartContext + WishlistContext), not a new
  // combined backend endpoint: neither needs to succeed atomically with the other, and
  // both contexts already own their own toast/error handling.
  async function handleMoveToCart(item) {
    await cart.addItem({ id: item.productId }, 1);
    await wishlist.removeItem(item.productId);
  }

  return (
    <>
      <AppHeader />
      <div className="wishlist-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <h1>Your saved items</h1>

        {wishlist.items.length === 0 ? (
          <div className="wishlist-empty">
            <p className="text-muted">Nothing saved yet.</p>
            <Link to="/" className="btn-secondary">
              Browse products
            </Link>
          </div>
        ) : (
          <ul className="wishlist-items">
            {wishlist.items.map((item) => (
              <li className="wishlist-item" key={item.productId}>
                <Link to={`/products/${item.productId}`} className="wishlist-item-info">
                  <span className="wishlist-item-thumb">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt={item.name} />
                    ) : (
                      <span className="wishlist-item-thumb-placeholder" aria-hidden="true" />
                    )}
                  </span>
                  <span className="wishlist-item-text">
                    <span className="wishlist-item-name">{item.name}</span>
                    <span className="text-muted">{formatPrice(item.unitPrice)}</span>
                  </span>
                </Link>
                <div className="wishlist-item-controls">
                  <button type="button" className="btn-secondary" onClick={() => handleMoveToCart(item)}>
                    Move to cart
                  </button>
                  <button
                    type="button"
                    className="wishlist-remove"
                    onClick={() => wishlist.removeItem(item.productId)}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
