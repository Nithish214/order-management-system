import { createContext, useContext, useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useApiFetch } from "../api/useApiFetch";
import { useToast } from "../toast/ToastContext";

// Same Context shape as CartContext -- CartProvider's own comment names "a shopping
// wishlist" as the next natural example of this exact pattern, and this is it: a
// Provider holding useState, a set of thin wrappers that call the Gateway and store
// whatever it hands back, and a hook to consume it. Server-side (order-service's
// WishlistController/WishlistItem table), same reasoning as the cart: it follows the
// account across devices, not just the one browser it was built up in.
const WishlistContext = createContext(null);

export function WishlistProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const apiFetch = useApiFetch();
  const { showToast } = useToast();

  // Items shaped as: { productId, sku, name, unitPrice, imageUrl, savedAt }
  const [items, setItems] = useState([]);

  // Same load-on-identity-change reasoning as CartContext's own effect.
  useEffect(() => {
    if (!isAuthenticated) {
      setItems([]);
      return;
    }
    let cancelled = false;
    apiFetch("/users/me/wishlist")
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (!cancelled && data) setItems(data);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, apiFetch]);

  // isSaved is used on every product card in the grid, so this is a Set (O(1) lookup)
  // rather than items.some(...) run once per card per render.
  const savedProductIds = new Set(items.map((item) => item.productId));

  async function saveItem(product) {
    const response = await apiFetch(`/users/me/wishlist/${product.id}`, { method: "POST" });
    if (response.ok) {
      setItems(await response.json());
      showToast("Saved for later");
    }
  }

  async function removeItem(productId) {
    const response = await apiFetch(`/users/me/wishlist/${productId}`, { method: "DELETE" });
    if (response.ok) {
      setItems(await response.json());
      showToast("Removed from your list");
    }
  }

  // The single toggle button every wishlist heart actually calls -- callers never need
  // to know or check isSaved themselves first.
  function toggleItem(product) {
    return savedProductIds.has(product.id) ? removeItem(product.id) : saveItem(product);
  }

  return (
    <WishlistContext.Provider value={{ items, savedProductIds, saveItem, removeItem, toggleItem }}>
      {children}
    </WishlistContext.Provider>
  );
}

export function useWishlist() {
  const context = useContext(WishlistContext);
  if (context === null) {
    throw new Error("useWishlist must be used within a WishlistProvider");
  }
  return context;
}
