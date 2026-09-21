import { Link } from "react-router-dom";
import Logo from "../components/Logo";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./NotFoundPage.css";

// The one route that intentionally sits outside ProtectedRoute (see App.jsx) -- a
// mistyped or dead URL should show a plain "not found" message regardless of whether
// you're currently logged in, not silently redirect to /login as if the real problem
// were your session. No AppHeader either, deliberately -- this can be reached by a
// logged-out visitor, so it stays as bare and self-contained as the auth pages -- but
// still gets the plain Logo those pages show, so even landing on a dead link doesn't
// look like it belongs to a different, unbranded site.
export default function NotFoundPage() {
  useDocumentTitle("Page not found");

  return (
    <div className="not-found-page">
      <Logo />
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
