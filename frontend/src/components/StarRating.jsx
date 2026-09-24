import "./StarRating.css";

const STAR_VALUES = [1, 2, 3, 4, 5];

// Two modes in one component: plain display (no onChange -- the average-rating summary,
// each review's own rating) and interactive picker (onChange provided -- the "write a
// review" form). Filled with --color-accent, not a conventional yellow/gold -- this app's
// whole design direction is deliberately one accent color for everything meaningful (see
// tokens.css's own comment), and a star rating is exactly the kind of decorative-color
// temptation that comment warns against reaching for.
export default function StarRating({ rating, onChange, size = 18 }) {
  const interactive = typeof onChange === "function";

  return (
    <span className={"star-rating" + (interactive ? " star-rating-interactive" : "")} role={interactive ? "radiogroup" : "img"} aria-label={interactive ? undefined : `${rating ?? 0} out of 5 stars`}>
      {STAR_VALUES.map((value) => {
        // Fractional fill for the read-only average (e.g. 4.3 shades the 5th star
        // partially) -- clamped 0-1 so a value like 2 against star 5 renders fully empty,
        // not a negative fill.
        const fillFraction = interactive ? (value <= rating ? 1 : 0) : Math.max(0, Math.min(1, (rating ?? 0) - (value - 1)));
        const star = (
          <svg
            key={value}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            aria-hidden="true"
            className="star-rating-star"
          >
            <defs>
              <linearGradient id={`star-fill-${value}-${size}`}>
                <stop offset={`${fillFraction * 100}%`} stopColor="var(--color-accent)" />
                <stop offset={`${fillFraction * 100}%`} stopColor="var(--color-border)" />
              </linearGradient>
            </defs>
            <path
              d="M12 2.5l2.9 6.6 7.1.7-5.4 4.7 1.6 7-6.2-3.8-6.2 3.8 1.6-7-5.4-4.7 7.1-.7z"
              fill={`url(#star-fill-${value}-${size})`}
            />
          </svg>
        );

        if (!interactive) return star;

        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={value === rating}
            aria-label={`${value} star${value === 1 ? "" : "s"}`}
            className="star-rating-button"
            onClick={() => onChange(value)}
          >
            {star}
          </button>
        );
      })}
    </span>
  );
}
