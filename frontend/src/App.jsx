import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { CartProvider } from "./cart/CartContext";
import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";
import ProductsPage from "./pages/ProductsPage";
import ProductDetailPage from "./pages/ProductDetailPage";
import OrderStatusPage from "./pages/OrderStatusPage";
import OrderHistoryPage from "./pages/OrderHistoryPage";
import CartPage from "./pages/CartPage";
import NotFoundPage from "./pages/NotFoundPage";

// The "protected route" pattern: a wrapper that checks auth state and either renders
// its children or redirects. <Navigate> is React Router's declarative way to redirect --
// you render it like any other element, and the router handles actually changing the
// URL/view, rather than you imperatively calling some "goToLogin()" function.
// `replace` means this redirect doesn't add a new browser-history entry, so the back
// button doesn't take you to a page you were bounced away from.
function ProtectedRoute({ children }) {
  const { isAuthenticated, isBootstrapping } = useAuth();

  // On first load (or any reload), AuthProvider starts with accessToken = null and
  // spends a moment asking the Gateway whether the httpOnly refresh cookie holds a
  // still-valid session (see AuthContext's bootstrapping effect). Rendering nothing
  // until that finishes avoids bouncing an already-logged-in user to /login for the
  // one render where isAuthenticated is still (incorrectly) false.
  if (isBootstrapping) {
    return null;
  }

  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

// <Routes> looks at the current URL and renders whichever single <Route> matches it --
// this is the whole mechanism that lets "the page" change without a full browser
// reload, and lets a URL like /orders/5 (Phase C) directly address one specific order.
function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <ProductsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/products/:id"
        element={
          <ProtectedRoute>
            <ProductDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/orders/:id"
        element={
          <ProtectedRoute>
            <OrderStatusPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/orders"
        element={
          <ProtectedRoute>
            <OrderHistoryPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/cart"
        element={
          <ProtectedRoute>
            <CartPage />
          </ProtectedRoute>
        }
      />
      {/* Deliberately NOT wrapped in ProtectedRoute -- an unmatched URL should show a
          plain "not found" page regardless of login state, not redirect to /login as if
          the real problem were your session. "*" matches anything none of the routes
          above already claimed. */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <AppRoutes />
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
