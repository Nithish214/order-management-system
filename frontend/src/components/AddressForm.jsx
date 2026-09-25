import { useId, useState } from "react";
import { friendlyErrorMessage } from "../utils/errors";
import Spinner from "./Spinner";

const EMPTY = { label: "", line1: "", line2: "", city: "", state: "", postalCode: "", country: "" };
const REQUIRED = ["label", "line1", "city", "state", "postalCode", "country"];

// Add and edit share this one form -- the same fields either way, and the backend's PUT
// replaces the whole address just like POST creates a whole one. The parent decides what
// "submit" means (create vs update) and what happens afterwards; this only collects, checks
// and hands over the values.
//
// Required-field checks here are for the person's benefit (an instant, specific message next
// to the empty field); the backend validates the same fields regardless -- this is never the
// only line of defense.
//
// defaultLocked: this address IS the default already (editing the current default, or saving
// the very first one), so "make it the default" is shown ticked and disabled instead of being
// a choice that would do nothing -- or, if unticked, would strand the user with no default.
export default function AddressForm({ initial, onSubmit, onCancel, submitLabel = "Save address", defaultLocked = false }) {
  const formId = useId();
  const [values, setValues] = useState(() => ({ ...EMPTY, ...withoutNulls(initial) }));
  const [makeDefault, setMakeDefault] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function setField(name, value) {
    setValues((current) => ({ ...current, [name]: value }));
    // Clears just this field's message the moment they start fixing it.
    if (fieldErrors[name]) {
      setFieldErrors((current) => {
        const remaining = { ...current };
        delete remaining[name];
        return remaining;
      });
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const problems = {};
    for (const name of REQUIRED) {
      if (!values[name].trim()) problems[name] = "Required";
    }
    if (Object.keys(problems).length > 0) {
      setFieldErrors(problems);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        label: values.label.trim(),
        line1: values.line1.trim(),
        line2: values.line2.trim() || null,
        city: values.city.trim(),
        state: values.state.trim(),
        postalCode: values.postalCode.trim(),
        country: values.country.trim(),
        isDefault: defaultLocked || makeDefault,
      });
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  function field(name, label, { optional = false, maxLength, autoComplete, placeholder, type = "text" } = {}) {
    const id = `${formId}-${name}`;
    const problem = fieldErrors[name];
    return (
      <div className={"form-field" + (problem ? " form-field--invalid" : "")}>
        <label htmlFor={id}>
          {label}
          {optional && <span className="form-optional"> (optional)</span>}
        </label>
        <input
          id={id}
          type={type}
          value={values[name]}
          onChange={(event) => setField(name, event.target.value)}
          maxLength={maxLength}
          autoComplete={autoComplete}
          placeholder={placeholder}
          aria-invalid={problem ? "true" : undefined}
          aria-describedby={problem ? `${id}-error` : undefined}
          disabled={submitting}
        />
        {problem && (
          <p className="text-error form-field-error" id={`${id}-error`}>
            {problem}
          </p>
        )}
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {field("label", "Label", { maxLength: 50, autoComplete: "off", placeholder: "Home, Work..." })}
      {field("line1", "Address line 1", { maxLength: 200, autoComplete: "address-line1" })}
      {field("line2", "Address line 2", { optional: true, maxLength: 200, autoComplete: "address-line2" })}
      <div className="form-row">
        {field("city", "City", { maxLength: 100, autoComplete: "address-level2" })}
        {field("state", "State / region", { maxLength: 100, autoComplete: "address-level1" })}
      </div>
      <div className="form-row">
        {field("postalCode", "Postal code", { maxLength: 20, autoComplete: "postal-code" })}
        {field("country", "Country", { maxLength: 100, autoComplete: "country-name" })}
      </div>

      <label className="form-checkbox">
        <input
          type="checkbox"
          checked={defaultLocked || makeDefault}
          disabled={defaultLocked || submitting}
          onChange={(event) => setMakeDefault(event.target.checked)}
        />
        {defaultLocked ? "This is your default address" : "Make this my default address"}
      </label>

      {error && <p className="text-error form-field-error">{error}</p>}

      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting && <Spinner size={14} />}
          {submitting ? "Saving..." : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}

function withoutNulls(address) {
  if (!address) return {};
  return Object.fromEntries(Object.entries(address).filter(([, value]) => value !== null && value !== undefined));
}
