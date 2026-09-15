import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import "./AuthForm.css";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      // Now that real routes exist, becoming authenticated doesn't move you off
      // /login by itself -- <Routes> only re-evaluates which page to show when the
      // URL changes, so we navigate explicitly. (In Phase A, before routing existed,
      // App's plain isAuthenticated ? ... : ... conditional handled this automatically --
      // that trick goes away once "which page" is driven by the URL instead.)
      navigate("/");
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <div className="auth-field">
          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="auth-field">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && <p className="text-error auth-error">{error}</p>}
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? "Logging in..." : "Log in"}
        </button>
      </form>
      <p className="auth-footer">
        <Link to="/signup">Don't have an account? Sign up</Link>
      </p>
    </div>
  );
}
