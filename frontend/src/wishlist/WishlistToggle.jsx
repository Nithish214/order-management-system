import { HeartIcon } from "../components/icons";
import { useWishlist } from "./WishlistContext";
import "./WishlistToggle.css";

// One button, two call sites (the product grid card, the detail page) -- both just need
// "this product, toggled" and nothing else, so the whole saved/not-saved check and the
// actual API call stay inside WishlistContext rather than each caller re-deriving them.
// A stopPropagation is needed only when this sits INSIDE a clickable card link (the grid);
// the detail page passes none, since it isn't nested in one.
export default function WishlistToggle({ product, onClick }) {
  const { savedProductIds, toggleItem } = useWishlist();
  const saved = savedProductIds.has(product.id);
  const label = saved ? `Remove ${product.name} from your saved list` : `Save ${product.name} for later`;

  function handleClick(e) {
    onClick?.(e);
    toggleItem(product);
  }

  return (
    <button
      type="button"
      className={`wishlist-toggle${saved ? " wishlist-toggle--saved" : ""}`}
      onClick={handleClick}
      aria-pressed={saved}
      aria-label={label}
      title={label}
    >
      <HeartIcon size={18} filled={saved} />
    </button>
  );
}
