import { createContext, useContext, useState } from "react";

// Same Context pattern as AuthContext -- this is genuinely the recurring shape for
// "some piece of state that many unrelated components need to read or change": a
// Provider holding useState, a set of functions that call the setter, and a hook to
// consume it. Once you've seen this shape once (AuthContext), a cart, a shopping
// wishlist, a theme toggle -- they're all this same pattern with different data.
const CartContext = createContext(null);

export function CartProvider({ children }) {
  // Cart items shaped as: { productId, sku, name, unitPrice, quantity }
  const [items, setItems] = useState([]);

  function addItem(product) {
    setItems((prev) => {
      const existing = prev.find((item) => item.productId === product.id);
      if (existing) {
        return prev.map((item) =>
          item.productId === product.id ? { ...item, quantity: item.quantity + 1 } : item
        );
      }
      return [
        ...prev,
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          unitPrice: product.unitPrice,
          quantity: 1,
        },
      ];
    });
  }

  function setQuantity(productId, quantity) {
    if (quantity <= 0) {
      removeItem(productId);
      return;
    }
    setItems((prev) =>
      prev.map((item) => (item.productId === productId ? { ...item, quantity } : item))
    );
  }

  function removeItem(productId) {
    setItems((prev) => prev.filter((item) => item.productId !== productId));
  }

  function clear() {
    setItems([]);
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
