import "./Spinner.css";

// A single reusable spinner rather than every loading state inventing its own -- used
// both inline in a button (replacing/preceding its label while an action is in flight)
// and next to a full-page "Loading..." message. Sized via a prop instead of a second
// component or size-specific class, since every call site so far just wants "small
// inline" or "default" and a plain number covers both without new variants.
export default function Spinner({ size = 16 }) {
  return (
    <span
      className="spinner"
      style={{ width: size, height: size }}
      role="status"
      aria-label="Loading"
    />
  );
}
