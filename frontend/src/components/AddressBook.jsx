import { useCallback, useEffect, useState } from "react";
import { useApiFetch } from "../api/useApiFetch";
import { createAddress, deleteAddress, getAddresses, updateAddress } from "../api/profile";
import { useToast } from "../toast/ToastContext";
import { friendlyErrorMessage } from "../utils/errors";
import AddressForm from "./AddressForm";
import AddressLines from "./AddressLines";
import Skeleton from "./Skeleton";
import "./AddressBook.css";

// Mirrors the backend's cap (AddressService.MAX_ADDRESSES_PER_USER). Only used to explain why
// "Add" is disabled -- the server enforces the real limit either way.
const MAX_ADDRESSES = 10;

// The address book section of the profile page: list, add, edit, delete, make default.
//
// Every change re-reads the whole list from the server instead of patching local state by hand:
// making one address the default changes a SECOND row (the old default), and deleting the
// default promotes another -- the server owns those rules, so the screen just shows whatever
// it says the book now looks like.
export default function AddressBook() {
  const apiFetch = useApiFetch();
  const { showToast } = useToast();
  const [addresses, setAddresses] = useState(null);
  const [error, setError] = useState(null);
  // null | "add" | the id of the address being edited -- only one form open at a time.
  const [openForm, setOpenForm] = useState(null);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    try {
      setAddresses(await getAddresses(apiFetch));
      setError(null);
    } catch (err) {
      setError(friendlyErrorMessage(err));
    }
  }, [apiFetch]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleCreate(payload) {
    await createAddress(apiFetch, payload);
    await load();
    setOpenForm(null);
    showToast("Address saved");
  }

  async function handleUpdate(id, payload) {
    await updateAddress(apiFetch, id, payload);
    await load();
    setOpenForm(null);
    showToast("Address updated");
  }

  async function handleMakeDefault(address) {
    setBusyId(address.id);
    try {
      await updateAddress(apiFetch, address.id, { ...toPayload(address), isDefault: true });
      await load();
      showToast("Default address changed");
    } catch (err) {
      showToast(friendlyErrorMessage(err), "error");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(address) {
    setBusyId(address.id);
    try {
      await deleteAddress(apiFetch, address.id);
      setConfirmingDeleteId(null);
      await load();
      showToast("Address deleted");
    } catch (err) {
      showToast(friendlyErrorMessage(err), "error");
    } finally {
      setBusyId(null);
    }
  }

  if (error && !addresses) {
    return (
      <div>
        <p className="text-error">{error}</p>
        <button type="button" className="btn-secondary" onClick={load}>
          Try again
        </button>
      </div>
    );
  }

  if (!addresses) {
    return (
      <ul className="address-list" aria-hidden="true">
        {[0, 1].map((index) => (
          <li className="address-card" key={index}>
            <Skeleton className="skeleton-text skeleton-address-title" />
            <Skeleton className="skeleton-text skeleton-address-line" />
          </li>
        ))}
      </ul>
    );
  }

  const atLimit = addresses.length >= MAX_ADDRESSES;

  return (
    <div>
      {addresses.length === 0 && openForm !== "add" && (
        <p className="text-muted address-empty">
          No saved addresses yet. Add one to check out faster, and it will be pre-selected at checkout.
        </p>
      )}

      <ul className="address-list">
        {addresses.map((address) => (
          <li className="address-card" key={address.id}>
            {openForm === address.id ? (
              <AddressForm
                initial={address}
                submitLabel="Save changes"
                defaultLocked={address.isDefault}
                onSubmit={(payload) => handleUpdate(address.id, payload)}
                onCancel={() => setOpenForm(null)}
              />
            ) : (
              <>
                <div className="address-card-head">
                  <span className="address-card-label">{address.label}</span>
                  {address.isDefault && <span className="address-badge">Default</span>}
                </div>
                <AddressLines address={address} />

                {confirmingDeleteId === address.id ? (
                  <div className="address-confirm" role="alertdialog" aria-label="Confirm delete">
                    <p className="text-muted">
                      Delete this address? Orders you've already placed keep the address they were sent to.
                    </p>
                    <div className="form-actions">
                      <button
                        type="button"
                        className="btn-primary"
                        onClick={() => handleDelete(address)}
                        disabled={busyId === address.id}
                      >
                        {busyId === address.id ? "Deleting..." : "Delete"}
                      </button>
                      <button type="button" className="btn-secondary" onClick={() => setConfirmingDeleteId(null)}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="address-card-actions">
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => {
                        setOpenForm(address.id);
                        setConfirmingDeleteId(null);
                      }}
                    >
                      Edit
                    </button>
                    {!address.isDefault && (
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => handleMakeDefault(address)}
                        disabled={busyId === address.id}
                      >
                        Make default
                      </button>
                    )}
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => {
                        setConfirmingDeleteId(address.id);
                        setOpenForm(null);
                      }}
                    >
                      Delete
                    </button>
                  </div>
                )}
              </>
            )}
          </li>
        ))}
      </ul>

      {openForm === "add" ? (
        <div className="address-card address-card--new">
          <h3>New address</h3>
          <AddressForm
            // The very first address is always the default -- nothing to choose.
            defaultLocked={addresses.length === 0}
            onSubmit={handleCreate}
            onCancel={() => setOpenForm(null)}
          />
        </div>
      ) : (
        <div className="address-add">
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              setOpenForm("add");
              setConfirmingDeleteId(null);
            }}
            disabled={atLimit}
          >
            Add a new address
          </button>
          {atLimit && <span className="text-muted"> You've saved the maximum of {MAX_ADDRESSES} addresses.</span>}
        </div>
      )}
    </div>
  );
}

// The editable fields only -- what PUT expects (no id, no isDefault).
function toPayload(address) {
  const { label, line1, line2, city, state, postalCode, country } = address;
  return { label, line1, line2, city, state, postalCode, country };
}
