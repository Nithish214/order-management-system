import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { useCurrency } from "../currency/CurrencyContext";
import CartSummary from "../cart/CartSummary";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import AppHeader from "../components/AppHeader";
import Spinner from "../components/Spinner";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./ProductsPage.css";

// Admin image-upload and restock controls used to live inline in this page's product
// rows -- moved out entirely as part of the grid redesign (ProductDetailPage already has
// both, fully working, since the multi-image feature). A browse-and-shop grid showing
// image/name/price/Add-to-Cart per card is a genuinely different job from "manage this
// one product's stock and photos," and cramming admin controls into every card would
// clutter exactly the clean grid this redesign is building. Nothing is lost -- it's on
// the page that's actually about one product, not scattered across all fifty at once.
export default function ProductsPage() {
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { isAdmin } = useAuth();
  const { formatPrice } = useCurrency();

  const [products, setProducts] = useState([]);
  // Keyed by productId -- Inventory Service's own data (GET /stock), fetched
  // independently of /products (see the separate effects below) rather than merged into
  // the product objects, since the two come from two different services and the same
  // product's price/name and its live stock level can each change independently -- and
  // since stock doesn't need refetching every time a search or category narrows down
  // which products are showing.
  const [stockByProductId, setStockByProductId] = useState({});
  // One row per category with its product count -- backs the sidebar (see
  // ProductController.getCategories). Loaded once; a failure here is treated as
  // non-fatal (see the effect below) since browsing the full catalog still works even
  // if the sidebar's category list can't populate.
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  // Bumped by the products-error "Try again" link further down -- included in the
  // products effect's dependency array purely as a re-run trigger.
  const [retryCount, setRetryCount] = useState(0);
  // What's actually in the search box right now, updated on every keystroke.
  const [searchQuery, setSearchQuery] = useState("");
  // What the product-fetching effect below actually reacts to -- deliberately NOT the
  // same state as searchQuery. Debouncing (see the effect that updates this, further
  // down) means a request only actually fires ~300ms after you stop typing, not on
  // every single keystroke -- long enough to feel instant, short enough that firing a
  // network request per letter typed would be wasteful for both this app and the
  // backend it's calling.
  const [debouncedQuery, setDebouncedQuery] = useState("");
  // "" means "All Products" -- deliberately not null/undefined, so it composes simply
  // with the search-vs-category precedence in the products effect below (an empty
  // string is falsy, same as no search query being falsy) without a separate null check.
  const [selectedCategory, setSelectedCategory] = useState("");
  // Separate from `loading` on purpose: `loading` gates the full-page "Loading
  // products..." replacement below, which should only ever happen once, on the very
  // first load. Every search or category switch after that also needs loading-state
  // treatment -- but reusing `loading` for that would unmount the entire page (search
  // box and category sidebar included) every time, fighting anyone still interacting
  // with either. `searching` drives a much smaller, inline indicator instead (see the
  // product-grid section), leaving the rest of the page untouched while a new set of
  // results comes back.
  const [searching, setSearching] = useState(false);
  const isFirstProductLoad = useRef(true);
  // 0-indexed, matching the backend's Spring Data Pageable convention directly rather
  // than translating back and forth -- only the *display* ("Page 1 of 4") adds 1.
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  // Client-side now, not sent to the backend at all -- see the visibleProducts useMemo
  // below. "featured" means "whatever order the server already returned" (id-ascending),
  // not a reorder of its own. Only meaningful while browsing/category-filtering: an
  // actual keyword search always stays ranked by relevance server-side, so the dropdown
  // for it is hidden below while debouncedQuery is set, same as before -- re-sorting a
  // relevance-ranked search result by price would throw away the one thing search itself
  // is for.
  const [sort, setSort] = useState("featured");
  // Both plain strings (not numbers) so an empty input reads as "no bound" without a
  // separate null/undefined case to juggle -- see visibleProducts below for how they're
  // parsed. Also client-side only, same reasoning as sort.
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const hasActiveSortOrFilter = sort !== "featured" || minPrice !== "" || maxPrice !== "";

  useDocumentTitle(debouncedQuery ? `Search: "${debouncedQuery}"` : selectedCategory || "Products");

  // productId -> quantity currently in the cart, derived from cart.items (the cart is
  // the single source of truth -- no separate "did I just add this" flag to keep in
  // sync). Once a card's product has a quantity here, its own Add-to-cart button is
  // replaced by the same quantity-stepper the cart panel uses (see the grid below), so
  // the "yes, this is in your cart, and here's how many" answer lives on the card
  // itself, not only in the sidebar.
  const cartQuantityByProductId = useMemo(
    () => Object.fromEntries(cart.items.map((item) => [item.productId, item.quantity])),
    [cart.items]
  );

  // Sort and price-filter, both applied entirely client-side to whatever page of
  // products the server already returned -- no new request for either. That does mean
  // both only ever act on the CURRENT page, not the whole category if it spans more than
  // one (see NOTES.md: correctly sorting/filtering an entire multi-page category would
  // need the backend's own search/list endpoints to accept a price range and a client
  // sort key, which is out of scope for a frontend-only pass).
  const visibleProducts = useMemo(() => {
    const min = minPrice === "" ? null : Number(minPrice);
    const max = maxPrice === "" ? null : Number(maxPrice);
    const filtered = products.filter((product) => {
      if (min !== null && !Number.isNaN(min) && product.unitPrice < min) return false;
      if (max !== null && !Number.isNaN(max) && product.unitPrice > max) return false;
      return true;
    });

    // .slice() first -- Array#sort mutates in place, and `products` (the effect's own
    // fetched state) must never be touched directly, or the "featured" (server) order
    // would be permanently lost the moment someone picked a different sort once.
    switch (sort) {
      case "price_asc":
        return filtered.slice().sort((a, b) => a.unitPrice - b.unitPrice);
      case "price_desc":
        return filtered.slice().sort((a, b) => b.unitPrice - a.unitPrice);
      case "name_asc":
        return filtered.slice().sort((a, b) => a.name.localeCompare(b.name));
      // No real createdAt field reaches the frontend at all, but id is assigned in
      // insertion order, so descending-id is exactly "most recently added" without
      // needing one -- see NOTES.md for why this is a legitimate client-side stand-in,
      // not a guess.
      case "newest":
        return filtered.slice().sort((a, b) => b.id - a.id);
      case "featured":
      default:
        return filtered;
    }
  }, [products, sort, minPrice, maxPrice]);

  function handleResetSortAndFilter() {
    setSort("featured");
    setMinPrice("");
    setMaxPrice("");
  }

  // Stock: fetched exactly once, on mount -- unlike products (below), it never needs
  // refetching just because a search or category narrows down which rows are showing.
  // Admin-only now (see StockCount's render further down): a regular shopper never sees
  // stock levels at all, so there's nothing for this effect to do for them -- skipping
  // the fetch entirely, not just hiding the result, avoids an unused network request on
  // every single page load for the vast majority of visitors.
  useEffect(() => {
    if (!isAdmin) {
      return;
    }

    let cancelled = false;

    async function loadStock() {
      try {
        const stockResponse = await apiFetch("/stock");
        if (!stockResponse.ok) {
          const body = await stockResponse.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load stock");
        }
        const stockData = await stockResponse.json();
        if (!cancelled) {
          setStockByProductId(
            Object.fromEntries(stockData.map((stock) => [stock.productId, stock.availableQuantity]))
          );
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyErrorMessage(err));
        }
      }
    }

    loadStock();

    return () => {
      cancelled = true;
    };
  }, [apiFetch, isAdmin]);

  // Categories: also fetched exactly once, on mount -- the sidebar's list doesn't change
  // based on what's currently selected or searched for.
  useEffect(() => {
    let cancelled = false;

    async function loadCategories() {
      try {
        const response = await apiFetch("/products/categories");
        if (!response.ok) return;
        const data = await response.json();
        if (!cancelled) {
          setCategories(data);
        }
      } catch {
        // Deliberately swallowed, not surfaced via setError: the sidebar just won't
        // show category options if this fails, but the main grid (driven by the
        // separate products effect below) still works fine either way -- a broken
        // sidebar shouldn't block browsing the products that already loaded.
      }
    }

    loadCategories();

    return () => {
      cancelled = true;
    };
  }, [apiFetch]);

  // Debounce: only update debouncedQuery (which the effect below actually reacts to)
  // 300ms after the user stops typing. Every keystroke resets this timer via the
  // cleanup function -- clearTimeout on the PREVIOUS pending timer before starting a
  // new one -- so only the very last keystroke in a burst of typing ever actually
  // survives long enough to fire.
  useEffect(() => {
    const timeoutId = setTimeout(() => setDebouncedQuery(searchQuery), 300);
    return () => clearTimeout(timeoutId);
  }, [searchQuery]);

  // Whenever the actual filter changes (not every keystroke -- debouncedQuery, not
  // searchQuery), jump back to page 1. Without this, switching category/search while
  // sitting on, say, page 3 would either show an unrelated page of the NEW result set
  // or -- once that set has fewer pages -- silently go out of range. A separate effect
  // rather than resetting page inline in handleSearchChange/handleSelectCategory: this
  // reacts to the one thing that actually means "the filter changed," decoupled from
  // exactly which handler caused it.
  useEffect(() => {
    setPage(0);
  }, [debouncedQuery, selectedCategory]);

  // Products: re-fetched whenever debouncedQuery, selectedCategory, or page changes. A
  // search takes priority over a category selection when both happen to be set --
  // handleSearchChange/handleSelectCategory below keep them mutually exclusive from the
  // UI side already (picking a category clears the search box and vice versa), but the
  // precedence is enforced here too rather than only relying on the UI never letting
  // both be set at once.
  useEffect(() => {
    let cancelled = false;

    async function loadProducts() {
      // Only the very first load blanks the whole page (see the `if (loading)` early
      // return below) -- every search, category switch, or page change after that just
      // flips the small inline `searching` indicator instead.
      if (isFirstProductLoad.current) {
        setLoading(true);
      } else {
        setSearching(true);
      }
      setError(null);
      try {
        // 100 matches ProductController's own DEFAULT_PAGE_SIZE -- passed explicitly
        // rather than relying on that default so this stays correct even if the
        // backend's default ever changes independently. No `sort` param here anymore --
        // sort is applied entirely client-side now (see visibleProducts), so every fetch
        // just uses the server's own stable default (id-ascending) order.
        const params = new URLSearchParams({ page: String(page), size: "100" });
        let path;
        if (debouncedQuery) {
          params.set("q", debouncedQuery);
          path = `/products/search?${params}`;
        } else {
          if (selectedCategory) {
            params.set("category", selectedCategory);
          }
          path = `/products?${params}`;
        }
        const response = await apiFetch(path);
        // The gateway's circuit breaker (api-gateway's FallbackController) can return a
        // real, non-array 503 response when order-service is unhealthy -- without this
        // check, that shape would reach `data.content` below and crash instead of
        // showing the fallback's own clear message.
        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load products");
        }
        // { content, page, size, totalElements, totalPages } -- see PagedResponse.
        const data = await response.json();
        if (!cancelled) {
          setProducts(data.content);
          setTotalPages(data.totalPages);
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
          setSearching(false);
          isFirstProductLoad.current = false;
        }
      }
    }

    loadProducts();

    // The cleanup function: if this component unmounts, or the query/category/page
    // changes again before this fetch finishes (e.g. clicking a different category
    // right after typing something), this flips `cancelled` so the late-arriving
    // response doesn't call setState on a stale request -- without it, a slow response
    // for an OLDER selection could overwrite a newer, already-displayed result.
    return () => {
      cancelled = true;
    };
  }, [apiFetch, debouncedQuery, selectedCategory, page, retryCount]);

  function handleSearchChange(value) {
    setSearchQuery(value);
    // Search and category browsing are treated as two mutually exclusive ways to narrow
    // the grid, not combinable ones -- typing a search clears whatever category was
    // selected, so there's never a confusing state where a category looks selected in
    // the sidebar but the grid is actually showing search results instead.
    if (value) {
      setSelectedCategory("");
    }
  }

  function handleSelectCategory(category) {
    setSelectedCategory(category);
    setSearchQuery("");
    setDebouncedQuery("");
  }

  if (loading) {
    return (
      <>
        <AppHeader />
        <div className="products-page">
          <p className="text-muted loading-row">
            <Spinner /> Loading products...
          </p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader />
      <div className="products-page">
        <div className="products-intro">
          <h1 className="products-heading">Shop our catalog</h1>
          <p className="text-muted products-subheading">Browse by category or search to find what you need.</p>
        </div>

        {/* Deliberately outside the `loading`/`searching` conditionals -- this input
            must never unmount or lose focus while a search is in flight, which is the
            whole reason `searching` exists as a state separate from `loading` (see the
            product-fetching effect above). */}
        <div className="products-controls">
          <input
            type="search"
            className="product-search-input"
            placeholder="Search products..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            aria-label="Search products"
          />

          {/* Hidden during an actual keyword search, not just disabled -- a real search
              always stays ranked by relevance server-side, so re-sorting or price-
              filtering its results here would throw away the one thing search is
              actually for. Both are purely client-side now (see visibleProducts) --
              picking either never triggers a new request. */}
          {!debouncedQuery && (
            <>
              <label className="product-sort-control">
                Sort by
                <select
                  className={sort !== "featured" ? "control-active" : undefined}
                  value={sort}
                  onChange={(e) => setSort(e.target.value)}
                >
                  <option value="featured">Featured</option>
                  <option value="price_asc">Price: Low to High</option>
                  <option value="price_desc">Price: High to Low</option>
                  <option value="newest">Newest</option>
                  <option value="name_asc">Name: A-Z</option>
                </select>
              </label>

              <div className="price-filter">
                <label>
                  Min
                  <input
                    type="number"
                    min="0"
                    inputMode="decimal"
                    className={minPrice !== "" ? "control-active" : undefined}
                    placeholder="$0"
                    value={minPrice}
                    onChange={(e) => setMinPrice(e.target.value)}
                    aria-label="Minimum price (USD)"
                  />
                </label>
                <span aria-hidden="true">&ndash;</span>
                <label>
                  Max
                  <input
                    type="number"
                    min="0"
                    inputMode="decimal"
                    className={maxPrice !== "" ? "control-active" : undefined}
                    placeholder="Any"
                    value={maxPrice}
                    onChange={(e) => setMaxPrice(e.target.value)}
                    aria-label="Maximum price (USD)"
                  />
                </label>
              </div>

              {hasActiveSortOrFilter && (
                <button type="button" className="inline-retry" onClick={handleResetSortAndFilter}>
                  Reset
                </button>
              )}
            </>
          )}
        </div>

        {/* Inline, not a full-page replacement -- the search box and category sidebar
            (already rendered above/beside this) stay usable even while the products
            themselves failed to load, so switching category/search is itself often a
            valid way to recover, alongside the explicit retry link. */}
        {error && (
          <p className="text-error page-error">
            {error}{" "}
            <button type="button" className="inline-retry" onClick={() => setRetryCount((c) => c + 1)}>
              Try again
            </button>
          </p>
        )}

        <div className="products-layout">
          {/* A left column on wide screens, a horizontal scrollable bar above the grid
              on narrow ones (see the media query in ProductsPage.css) -- one category
              list either way, not two separate implementations to keep in sync. */}
          <aside className="category-sidebar" aria-label="Product categories">
            <h2 className="category-sidebar-heading">Categories</h2>
            <ul className="category-list">
              <li>
                <button
                  type="button"
                  className={
                    selectedCategory === "" ? "category-link category-link-active" : "category-link"
                  }
                  onClick={() => handleSelectCategory("")}
                >
                  All Products
                </button>
              </li>
              {categories.map((c) => (
                <li key={c.category}>
                  <button
                    type="button"
                    className={
                      selectedCategory === c.category
                        ? "category-link category-link-active"
                        : "category-link"
                    }
                    onClick={() => handleSelectCategory(c.category)}
                  >
                    {c.category} ({c.count})
                  </button>
                </li>
              ))}
            </ul>
          </aside>

          <section className="product-grid-section">
            {/* Several genuinely different messages, not the same text reused -- an
                empty catalog, "your search matched nothing," "this category has no
                products," and "the price filter excluded everything on this page" are
                all different situations, and conflating them would mislead whichever
                one is actually happening. */}
            {searching && (
              <p className="text-muted loading-row">
                <Spinner /> Loading...
              </p>
            )}
            {!searching && products.length === 0 && (
              <p className="text-muted">
                {debouncedQuery
                  ? `No products match "${debouncedQuery}".`
                  : selectedCategory
                    ? `No products in ${selectedCategory} right now.`
                    : "No products available right now."}
              </p>
            )}
            {!searching && products.length > 0 && visibleProducts.length === 0 && (
              <p className="text-muted">
                No products in this price range.{" "}
                <button type="button" className="inline-retry" onClick={handleResetSortAndFilter}>
                  Reset filter
                </button>
              </p>
            )}
            {!searching && visibleProducts.length > 0 && (
              <div className="product-grid">
                {visibleProducts.map((product) => (
                  <div className="product-card" key={product.id}>
                    <Link to={`/products/${product.id}`} className="product-card-link">
                      <div className="product-card-thumb">
                        {product.images.length > 0 ? (
                          <img src={product.images[0].imageUrl} alt={product.name} />
                        ) : (
                          <div className="product-thumb-placeholder" aria-hidden="true" />
                        )}
                      </div>
                      <p className="product-card-name">{product.name}</p>
                      <p className="product-card-price">{formatPrice(product.unitPrice)}</p>
                    </Link>
                    {/* Admin-only -- a regular shopper never sees stock levels, "Out of
                        stock" included. Adding an out-of-stock item to the cart still
                        works with no warning here; the order itself is still rejected
                        at checkout regardless (StockController's reservation logic
                        doesn't trust what this card shows either way). */}
                    {isAdmin && <StockCount quantity={stockByProductId[product.id]} />}
                    {cartQuantityByProductId[product.id] ? (
                      <div className="quantity-stepper product-card-stepper">
                        <button
                          type="button"
                          onClick={() =>
                            cart.setQuantity(product.id, cartQuantityByProductId[product.id] - 1)
                          }
                          aria-label={`Decrease quantity of ${product.name}`}
                        >
                          &minus;
                        </button>
                        <input
                          type="number"
                          min="1"
                          value={cartQuantityByProductId[product.id]}
                          onChange={(e) => cart.setQuantity(product.id, Number(e.target.value))}
                          aria-label={`Quantity of ${product.name}`}
                        />
                        <button
                          type="button"
                          onClick={() =>
                            cart.setQuantity(product.id, cartQuantityByProductId[product.id] + 1)
                          }
                          aria-label={`Increase quantity of ${product.name}`}
                        >
                          +
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="btn-secondary product-card-add"
                        onClick={() => cart.addItem(product)}
                      >
                        Add to cart
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Only when there's more than one page -- a single-page result (the
                common case for a category search, or the whole catalog once it's small
                enough) has nothing to page through, so Previous/Next would just be two
                permanently-disabled buttons taking up space for no reason. */}
            {!searching && totalPages > 1 && (
              <div className="product-pagination">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setPage((p) => p - 1)}
                  disabled={page === 0}
                >
                  Previous
                </button>
                <span className="product-pagination-status">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page + 1 >= totalPages}
                >
                  Next
                </button>
              </div>
            )}
          </section>

          <aside className="cart-sidebar">
            <CartSummary />
          </aside>
        </div>
      </div>
    </>
  );
}
