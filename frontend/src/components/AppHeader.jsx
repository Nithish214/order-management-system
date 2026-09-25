import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import Logo from "./Logo";
import { CartIcon } from "./icons";
import "./AppHeader.css";

// Persistent across every signed-in page (Products, Order History, Order Status, Cart) --
// not the quiet auth pages, which stay deliberately bare. Fixes a real gap: before this,
// "Log out" only existed on the Products page, so viewing your order history or an
// order's status left you with no way to log out without navigating back to "/" first.
export default function AppHeader() {
  const { logout, isAdmin } = useAuth();
  const cart = useCart();
  const itemCount = cart.items.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <header className="app-header">
      <Link to="/" className="app-header-brand">
        <Logo />
      </Link>
      <nav className="app-header-nav">
        <Link to="/">Products</Link>
        <Link to="/orders">Order history</Link>
        <Link to="/profile">Profile</Link>
        <Link to="/cart" className="app-header-cart-link">
          <CartIcon size={18} />
          Cart
          {itemCount > 0 && <span className="app-header-cart-badge">{itemCount}</span>}
        </Link>
        {isAdmin && <Link to="/admin">Dashboard</Link>}
        {isAdmin && <span className="app-header-admin-badge">Admin</span>}
        <button onClick={logout} className="btn-secondary">
          Log out
        </button>
      </nav>
    </header>
  );
}
