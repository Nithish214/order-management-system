import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signUp, confirmSignUp } from "../auth/cognito";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import Spinner from "../components/Spinner";
import Logo from "../components/Logo";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./AuthForm.css";

// Two-step flow, tracked with a plain "step" string in local state rather than separate
// routes -- there's nothing else that would ever need to link directly to "step 2",
// so a second route would just be overhead here (contrast with Phase C/D, where /orders/:id
// genuinely needed to be its own address).
export default function SignupPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState("register"); // "register" | "confirm"
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useDocumentTitle(step === "confirm" ? "Check your email" : "Create account");

  async function handleRegister(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await signUp(email, password);
      setStep("confirm");
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirm(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await confirmSignUp(email, code);
      // Auto-login right after confirming, using the same credentials they just set --
      // login() also syncs the real email onto app_user (see AuthContext), so this one
      // step both gets them in and fixes the placeholder-email problem immediately,
      // rather than waiting for whatever their *next* login happens to be.
      await login(email, password);
      navigate("/");
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-brand">
        <Logo size="lg" />
        <p className="text-muted auth-tagline">Browse products, add to cart, and check out in minutes.</p>
      </div>
      {step === "register" ? (
        <>
          <h1>Create account</h1>
          <form onSubmit={handleRegister}>
            <div className="auth-field">
              <label htmlFor="signup-email">Email</label>
              <input
                id="signup-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="auth-field">
              <label htmlFor="signup-password">Password</label>
              <input
                id="signup-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-error auth-error">{error}</p>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading && <Spinner size={14} />}
              {loading ? "Creating account..." : "Create account"}
            </button>
          </form>
          <p className="auth-footer">
            <Link to="/login">Already have an account? Log in</Link>
          </p>
        </>
      ) : (
        <>
          <h1>Check your email</h1>
          <p className="text-muted auth-hint">We sent a confirmation code to {email}.</p>
          <form onSubmit={handleConfirm}>
            <div className="auth-field">
              <label htmlFor="signup-code">Confirmation code</label>
              <input
                id="signup-code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-error auth-error">{error}</p>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading && <Spinner size={14} />}
              {loading ? "Confirming..." : "Confirm"}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
