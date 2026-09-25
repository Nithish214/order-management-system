import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { getProfile, updateProfile } from "../api/profile";
import { useToast } from "../toast/ToastContext";
import { friendlyErrorMessage } from "../utils/errors";
import AddressBook from "../components/AddressBook";
import AppHeader from "../components/AppHeader";
import ErrorState from "../components/ErrorState";
import Skeleton from "../components/Skeleton";
import Spinner from "../components/Spinner";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./ProfilePage.css";

export default function ProfilePage() {
  useDocumentTitle("Your profile");
  const apiFetch = useApiFetch();
  const { showToast } = useToast();

  // What the server currently has -- the form below edits a copy, so "Save" can stay disabled
  // until something actually differs from this.
  const [profile, setProfile] = useState(null);
  const [name, setName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [loadError, setLoadError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [nameError, setNameError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoadError(null);
      try {
        const loaded = await getProfile(apiFetch);
        if (cancelled) return;
        setProfile(loaded);
        setName(loaded.name ?? "");
        setPhoneNumber(loaded.phoneNumber ?? "");
      } catch (err) {
        if (cancelled) return;
        // 404 = this login has never created a profile row yet (the sync after login normally
        // does, but nothing guarantees it already ran). That's just an empty profile to fill in;
        // saving it creates the row. Anything else is a real failure.
        if (err.status === 404) {
          setProfile({ email: "", name: "", phoneNumber: null });
        } else {
          setLoadError(friendlyErrorMessage(err));
        }
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [apiFetch, retryCount]);

  async function handleSave(event) {
    event.preventDefault();
    if (!name.trim()) {
      setNameError("Required");
      return;
    }
    setNameError(null);
    setSaving(true);
    setSaveError(null);
    try {
      const saved = await updateProfile(apiFetch, { name: name.trim(), phoneNumber: phoneNumber.trim() || null });
      setProfile(saved);
      setName(saved.name ?? "");
      setPhoneNumber(saved.phoneNumber ?? "");
      showToast("Profile saved");
    } catch (err) {
      setSaveError(friendlyErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return (
      <>
        <AppHeader />
        <div className="profile-page">
          <ErrorState message={loadError} onRetry={() => setRetryCount((c) => c + 1)} />
        </div>
      </>
    );
  }

  const unchanged =
    profile !== null && name.trim() === (profile.name ?? "") && phoneNumber.trim() === (profile.phoneNumber ?? "");

  return (
    <>
      <AppHeader />
      <div className="profile-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <h1>Your profile</h1>

        <section className="profile-section">
          <h2>Details</h2>
          {!profile ? (
            <div aria-hidden="true">
              <Skeleton className="skeleton-text skeleton-profile-field" />
              <Skeleton className="skeleton-text skeleton-profile-field" />
              <Skeleton className="skeleton-text skeleton-profile-field" />
            </div>
          ) : (
            <form onSubmit={handleSave} noValidate>
              <div className="form-field">
                <label htmlFor="profile-email">Email</label>
                <input id="profile-email" type="email" value={profile.email} disabled readOnly />
                <p className="text-muted form-hint">This is your login, so it can't be changed here.</p>
              </div>

              <div className={"form-field" + (nameError ? " form-field--invalid" : "")}>
                <label htmlFor="profile-name">Name</label>
                <input
                  id="profile-name"
                  type="text"
                  value={name}
                  maxLength={255}
                  autoComplete="name"
                  onChange={(event) => {
                    setName(event.target.value);
                    setNameError(null);
                  }}
                  disabled={saving}
                  aria-invalid={nameError ? "true" : undefined}
                />
                {nameError && <p className="text-error form-field-error">{nameError}</p>}
              </div>

              <div className="form-field">
                <label htmlFor="profile-phone">
                  Phone number <span className="form-optional">(optional)</span>
                </label>
                <input
                  id="profile-phone"
                  type="tel"
                  value={phoneNumber}
                  maxLength={20}
                  autoComplete="tel"
                  placeholder="+44 20 7946 0958"
                  onChange={(event) => setPhoneNumber(event.target.value)}
                  disabled={saving}
                />
              </div>

              {saveError && <p className="text-error form-field-error">{saveError}</p>}

              <button type="submit" className="btn-primary" disabled={saving || unchanged}>
                {saving && <Spinner size={14} />}
                {saving ? "Saving..." : "Save changes"}
              </button>
            </form>
          )}
        </section>

        <section className="profile-section">
          <h2>Address book</h2>
          <p className="text-muted profile-section-note">
            Saved addresses you can pick from at checkout. Your default is pre-selected.
          </p>
          <AddressBook />
        </section>
      </div>
    </>
  );
}
