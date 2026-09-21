import { createContext, useCallback, useContext, useState } from "react";
import "./Toast.css";

// Same Context pattern as every other piece of shared state in this app (Auth, Cart,
// Currency) -- a Provider holding state, one function to change it, a hook to consume
// it. showToast is deliberately the only thing exposed: callers never need to touch the
// underlying array or worry about ids/timers themselves.
const ToastContext = createContext(null);

const AUTO_DISMISS_MS = 3000;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  // useCallback so this stays a stable reference -- CartContext calls this from inside
  // its own functions (addItem/setQuantity/removeItem), and a fresh function identity
  // on every render would be one more thing for any effect depending on it to worry
  // about, the same reasoning useApiFetch already documents for its own useCallback.
  const showToast = useCallback((message, type = "success") => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, AUTO_DISMISS_MS);
  }, []);

  function dismiss(id) {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {/* Rendered once, here, rather than per-page -- a toast can be triggered by code
          (CartContext) that has no idea which page happens to be mounted right now. */}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast--${toast.type}`}>
            <span>{toast.message}</span>
            <button
              type="button"
              className="toast-dismiss"
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss"
            >
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (context === null) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
