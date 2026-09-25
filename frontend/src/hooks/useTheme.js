import { useCallback, useEffect, useState } from "react";

// Theme = "light" | "dark", applied as data-theme on <html> (see tokens.css). The FIRST
// paint is handled by the inline script in index.html, so there is no white flash on a dark
// load; this hook just keeps React in step with it and handles the toggle.
//
// Until the visitor picks one, the theme follows the operating system and changes live if
// the OS does. Once they pick, that choice is stored and wins. localStorage is fine here
// (unlike tokens -- see AuthContext): a light/dark preference is not sensitive, and every
// access is wrapped because storage can be blocked or throw (private windows, blocked
// cookies) -- the theme must still work for the session in that case.
const STORAGE_KEY = "theme";

function readStored() {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === "light" || value === "dark" ? value : null;
  } catch {
    return null;
  }
}

function systemTheme() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export default function useTheme() {
  const [theme, setTheme] = useState(() => readStored() ?? systemTheme());

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  // Follow the OS only while the visitor hasn't chosen for themselves.
  useEffect(() => {
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => {
      if (readStored() === null) setTheme(systemTheme());
    };
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  const toggleTheme = useCallback(() => {
    setTheme((current) => {
      const next = current === "dark" ? "light" : "dark";
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Not persisted -- still applies for this visit.
      }
      return next;
    });
  }, []);

  return { theme, toggleTheme };
}
