import useTheme from "../hooks/useTheme";
import { MoonIcon, SunIcon } from "./icons";
import "./ThemeToggle.css";

// Icon-only, so the accessible name is the action it performs ("Switch to dark theme"),
// not its current state -- and title gives sighted mouse users the same words on hover.
export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const label = theme === "dark" ? "Switch to light theme" : "Switch to dark theme";

  return (
    <button type="button" className="theme-toggle" onClick={toggleTheme} aria-label={label} title={label}>
      {theme === "dark" ? <SunIcon size={18} /> : <MoonIcon size={18} />}
    </button>
  );
}
