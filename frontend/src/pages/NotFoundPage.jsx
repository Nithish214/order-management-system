import { Link } from "react-router-dom";
import "./NotFoundPage.css";

// The one route that intentionally sits outside ProtectedRoute (see App.jsx) -- a
// mistyped or dead URL should show a plain "not found" message regardless of whether
// you're currently logged in, not silently redirect to /login as if the real problem
// were your session. No AppHeader either, deliberately -- this can be reached by a
// logged-out visitor, so it stays as bare and self-contained as the auth pages.
export default function NotFoundPage() {
  return (
    <div className="not-found-page">
      <h1>Page not found</h1>
      <p className="text-muted">The page you&rsquo;re looking for doesn&rsquo;t exist.</p>
      <p>
        <Link to="/" className="back-link">
          &larr; Back to products
        </Link>
      </p>
    </div>
  );
}
