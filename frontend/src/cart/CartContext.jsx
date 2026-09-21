import { createContext, useContext, useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useApiFetch } from "../api/useApiFetch";

// Same Context pattern as AuthContext -- this is genuinely the recurring shape for
// "some piece of state that many unrelated components need to read or change": a
// Provider holding useState, a set of functions that call the setter, and a hook to
// consume it. Once you've seen this shape once (AuthContext), a cart, a shopping
// wishlist, a theme toggle -- they're all this same pattern with different data.
//
// The cart itself lives server-side now (order-service's CartController/CartItem
// table), not in this browser's localStorage -- so it follows an account across
// devices/browsers, not just the one browser it was built up in. Every function below
// is really just a thin wrapper: call the Gateway, take whatever cart state it hands
// back, and store that -- the server is the one source of truth throughout.
const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const apiFetch = useApiFetch();

  // Cart items shaped as: { productId, sku, name, unitPrice, quantity, imageUrl }
  const [items, setItems] = useState([]);

  // Loads this account's own server-side cart whenever the signed-in identity changes --
  // apiFetch's own identity changes exactly when accessToken does (see useApiFetch's
  // useCallback deps), which covers login, logout, AND switching to a different account
  // directly in the same tab (LoginPage doesn't redirect an already-authenticated visitor
  // away). isAuthenticated decides whether to actually fetch or just show an empty cart.
  useEffect(() => {
    if (!isAuthenticated) {
      setItems([]);
      return;
    }
    let cancelled = false;
    apiFetch("/cart")
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (!cancelled && data) setItems(data);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, apiFetch]);

  // quantity defaults to 1 -- every existing caller (ProductsPage's plain "Add to cart"
  // button) keeps its exact original behavior unchanged. The product detail page is the
  // only caller that passes a chosen quantity explicitly.
  async function addItem(product, quantity = 1) {
    const response = await apiFetch("/cart/items", {
      method: "POST",
      body: JSON.stringify({ productId: product.id, quantity }),
    });
    if (response.ok) setItems(await response.json());
  }

  // quantity <= 0 removes the item entirely -- CartController's own convention, same as
  // this function's previous client-only behavior.
  async function setQuantity(productId, quantity) {
    const response = await apiFetch(`/cart/items/${productId}`, {
      method: "PUT",
      body: JSON.stringify({ quantity }),
    });
    if (response.ok) setItems(await response.json());
  }

  async function removeItem(productId) {
    const response = await apiFetch(`/cart/items/${productId}`, {
      method: "DELETE",
    });
    if (response.ok) setItems(await response.json());
  }

  async function clear() {
    const response = await apiFetch("/cart", { method: "DELETE" });
    if (response.ok) setItems(await response.json());
  }

  const total = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);

  return (
    <CartContext.Provider value={{ items, addItem, setQuantity, removeItem, clear, total }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (context === null) {
    throw new Error("useCart must be used within a CartProvider");
  }
  return context;
}
