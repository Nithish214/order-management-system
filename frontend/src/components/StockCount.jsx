import "./StockCount.css";

// null/undefined means stock data simply hasn't arrived yet (or isn't available for this
// product) -- says nothing rather than guessing at a number. Zero gets its own distinct
// treatment (the one case genuinely worth calling out), everything else is quiet, muted
// text -- this is informational, not another status judgment competing with the order
// status page's colors for meaning.
export default function StockCount({ quantity }) {
  if (quantity == null) {
    return null;
  }
  if (quantity === 0) {
    return <span className="stock-count stock-count--out">Out of stock</span>;
  }
  return <span className="stock-count text-muted">{quantity} in stock</span>;
}
