import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signUp, confirmSignUp } from "../auth/cognito";
import { useAuth } from "../auth/AuthContext";

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

  async function handleRegister(event) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await signUp(email, password);
      setStep("confirm");
    } catch (err) {
      setError(err.message);
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
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ maxWidth: 320, margin: "80px auto", fontFamily: "sans-serif" }}>
      {step === "register" ? (
        <>
          <h1>Create account</h1>
          <form onSubmit={handleRegister}>
            <div style={{ marginBottom: 12 }}>
              <label>
                Email
                <br />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  style={{ width: "100%" }}
                />
              </label>
            </div>
            <div style={{ marginBottom: 12 }}>
              <label>
                Password
                <br />
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  style={{ width: "100%" }}
                />
              </label>
            </div>
            {error && <p style={{ color: "red" }}>{error}</p>}
            <button type="submit" disabled={loading}>
              {loading ? "Creating account..." : "Create account"}
            </button>
          </form>
          <p>
            <Link to="/login">Already have an account? Log in</Link>
          </p>
        </>
      ) : (
        <>
          <h1>Check your email</h1>
          <p>We sent a confirmation code to {email}.</p>
          <form onSubmit={handleConfirm}>
            <div style={{ marginBottom: 12 }}>
              <label>
                Confirmation code
                <br />
                <input
                  type="text"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  required
                  style={{ width: "100%" }}
                />
              </label>
            </div>
            {error && <p style={{ color: "red" }}>{error}</p>}
            <button type="submit" disabled={loading}>
              {loading ? "Confirming..." : "Confirm"}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
