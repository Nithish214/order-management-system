// Plain inline SVG, not an icon-font/library dependency -- one glyph, used in exactly two
// places (Logo's mark, AppHeader's cart link), doesn't justify pulling in a whole icon
// package. currentColor throughout means it automatically matches whatever text color
// surrounds it (white inside the Logo's accent-filled mark, the header's own text color
// for the plain nav link), same reasoning as Spinner's own currentColor choice.
export function CartIcon({ size = 20 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2 3h2l2.6 12.4a1 1 0 0 0 1 .8h9.7a1 1 0 0 0 1-.8L21 7H5.2" />
      <circle cx="9" cy="20" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="18" cy="20" r="1.5" fill="currentColor" stroke="none" />
    </svg>
  );
}
