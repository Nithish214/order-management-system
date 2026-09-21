import { Link } from "react-router-dom";
import "./Breadcrumbs.css";

// segments: [{ label, to? }, ...]. The LAST segment is always plain text, never a link,
// regardless of whether it has a `to` -- standard breadcrumb convention: you're already
// on this page, there's nothing to navigate to by clicking "here." Any earlier segment
// without a `to` (there currently isn't one, but a future caller might have a level with
// no real page to link to) also renders as plain text rather than a broken/dead link.
export default function Breadcrumbs({ segments }) {
  return (
    <nav className="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {segments.map((segment, index) => {
          const isCurrent = index === segments.length - 1;
          return (
            <li key={segment.label}>
              {isCurrent || !segment.to ? (
                <span aria-current={isCurrent ? "page" : undefined}>{segment.label}</span>
              ) : (
                <Link to={segment.to}>{segment.label}</Link>
              )}
              {!isCurrent && (
                <span className="breadcrumbs-separator" aria-hidden="true">
                  /
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
