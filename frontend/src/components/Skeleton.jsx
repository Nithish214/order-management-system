import "./Skeleton.css";

// One shared shimmer treatment (see Skeleton.css); every actual SHAPE (a card's thumb,
// a text line, a status pill) is a page-specific class composed on top of this in
// whichever page's own stylesheet needs it -- same "shared base behavior, page-specific
// sizing" split this app already uses for things like .btn-secondary.
export default function Skeleton({ className }) {
  return <div className={`skeleton ${className || ""}`} aria-hidden="true" />;
}
