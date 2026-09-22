import { useState } from "react";
import { startGoogleLogin } from "./google";
import Spinner from "../components/Spinner";
import "./GoogleSignInButton.css";

// Shared by LoginPage and SignupPage -- Google sign-in doubles as sign-up (Cognito
// creates the user on first successful federation, already email-verified by Google),
// so there's exactly one button, not a separate "sign up with Google" variant.
export default function GoogleSignInButton() {
  // Spinner-only, no error state here -- startGoogleLogin ends in a full-page redirect
  // to Google, so the only way this component is still mounted afterward is if that
  // redirect itself failed to even start (Web Crypto unavailable, e.g.), which is rare
  // enough not to warrant a dedicated inline error message the way a real login attempt
  // failing would.
  const [redirecting, setRedirecting] = useState(false);

  return (
    <button
      type="button"
      className="btn-secondary google-signin-button"
      disabled={redirecting}
      onClick={() => {
        setRedirecting(true);
        startGoogleLogin();
      }}
    >
      {redirecting ? (
        <Spinner size={14} />
      ) : (
        <svg className="google-signin-icon" viewBox="0 0 18 18" aria-hidden="true">
          <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.88 2.7-6.62z" />
          <path fill="#34A853" d="M9 18c2.43 0 4.47-.81 5.96-2.18l-2.9-2.26c-.81.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.9v2.33A9 9 0 0 0 9 18z" />
          <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 0 1 3.67 9c0-.59.1-1.17.28-1.7V4.97H.9A9 9 0 0 0 0 9c0 1.45.35 2.83.9 4.03l3.05-2.33z" />
          <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.46 3.44 1.35l2.58-2.58C13.47.89 11.43 0 9 0A9 9 0 0 0 .9 4.97l3.05 2.33C4.66 5.17 6.65 3.58 9 3.58z" />
        </svg>
      )}
      {redirecting ? "Redirecting to Google..." : "Continue with Google"}
    </button>
  );
}
