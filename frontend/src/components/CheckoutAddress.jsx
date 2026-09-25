import { useCallback, useEffect, useState } from "react";
import { useApiFetch } from "../api/useApiFetch";
import { createAddress, getAddresses } from "../api/profile";
import { friendlyErrorMessage } from "../utils/errors";
import AddressForm from "./AddressForm";
import AddressLines from "./AddressLines";
import "./CheckoutAddress.css";

// The "Ship to" choice shown right above "Place order". Preselects the user's default address;
// they can pick another saved one, or add a new one right here without leaving checkout.
//
// The selection itself (`value`) lives in the parent -- CartSummary needs it to send with the
// order and to decide whether "Place order" is enabled -- so this reports every change through
// onChange rather than keeping its own copy. `reloadToken`: the parent bumps it to force a
// fresh read, e.g. after the server rejected a stale address id at order time.
export default function CheckoutAddress({ value, onChange, reloadToken = 0 }) {
  const apiFetch = useApiFetch();
  const [addresses, setAddresses] = useState(null);
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      const loaded = await getAddresses(apiFetch);
      setAddresses(loaded);
      setError(null);
      return loaded;
    } catch (err) {
      setError(friendlyErrorMessage(err));
      return null;
    }
  }, [apiFetch]);

  useEffect(() => {
    load();
  }, [load, reloadToken]);

  // Once the list is known: keep the current choice if it's still one of them, otherwise fall
  // back to the default (the server guarantees a user with any address has exactly one), and to
  // "nothing selected" when there are none -- which is also what keeps "Place order" disabled.
  useEffect(() => {
    if (!addresses) return;
    if (addresses.some((address) => address.id === value)) return;
    const fallback = addresses.find((address) => address.isDefault) ?? addresses[0];
    onChange(fallback ? fallback.id : null);
  }, [addresses, value, onChange]);

  async function handleCreate(payload) {
    const created = await createAddress(apiFetch, payload);
    await load();
    onChange(created.id);
    setAdding(false);
  }

  if (error && !addresses) {
    return (
      <div className="checkout-address">
        <p className="text-error">{error}</p>
        <button type="button" className="btn-secondary" onClick={load}>
          Try again
        </button>
      </div>
    );
  }

  if (!addresses) {
    return <p className="text-muted checkout-address">Loading your addresses...</p>;
  }

  // Nothing saved yet: no list to choose from, so the add form IS the step -- and it can't be
  // cancelled, since there's nothing to fall back to.
  if (addresses.length === 0) {
    return (
      <fieldset className="checkout-address">
        <legend>Ship to</legend>
        <p className="text-muted checkout-address-intro">Add a shipping address to place your order.</p>
        <AddressForm defaultLocked submitLabel="Save address" onSubmit={handleCreate} />
      </fieldset>
    );
  }

  return (
    <fieldset className="checkout-address">
      <legend>Ship to</legend>
      <div className="checkout-address-options" role="radiogroup" aria-label="Shipping address">
        {addresses.map((address) => (
          <label
            key={address.id}
            className={"checkout-address-option" + (address.id === value ? " checkout-address-option--selected" : "")}
          >
            <input
              type="radio"
              name="shipping-address"
              checked={address.id === value}
              onChange={() => onChange(address.id)}
            />
            <span className="checkout-address-body">
              <span className="checkout-address-label">
                {address.label}
                {address.isDefault && <span className="text-muted"> · Default</span>}
              </span>
              <AddressLines address={address} />
            </span>
          </label>
        ))}
      </div>

      {adding ? (
        <div className="checkout-address-new">
          <AddressForm submitLabel="Save and use this address" onSubmit={handleCreate} onCancel={() => setAdding(false)} />
        </div>
      ) : (
        <button type="button" className="btn-secondary" onClick={() => setAdding(true)}>
          Add a new address
        </button>
      )}
    </fieldset>
  );
}
