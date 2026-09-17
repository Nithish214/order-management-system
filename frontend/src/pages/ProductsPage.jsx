import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import CartSummary from "../cart/CartSummary";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import AppHeader from "../components/AppHeader";
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
  // "featured" matches ProductController's own default -- the same stable id-ascending
  // order pagination already needs for correctness (see that controller's comment), not
  // a separate concept. Only meaningful while browsing/category-filtering: an actual
  // keyword search always stays ranked by relevance server-side regardless of this value
  // (see resolveSort's comment), so the dropdown for it is hidden below while
  // debouncedQuery is set, rather than offering a control that would silently do nothing.
  const [sort, setSort] = useState("featured");

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
  }, [debouncedQuery, selectedCategory, sort]);

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
      try {
        // 100 matches ProductController's own DEFAULT_PAGE_SIZE -- passed explicitly
        // rather than relying on that default so this stays correct even if the
        // backend's default ever changes independently.
        const params = new URLSearchParams({ page: String(page), size: "100", sort });
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
  }, [apiFetch, debouncedQuery, selectedCategory, page, sort]);

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
          <p className="text-muted">Loading products...</p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader />
      <div className="products-page">
        <h1 className="products-heading">Products</h1>

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
              always stays ranked by relevance server-side (see ProductController's
              buildPageable/resolveSort comments), so showing this control while
              searching would offer a choice that silently does nothing. */}
          {!debouncedQuery && (
            <label className="product-sort-control">
              Sort by
              <select value={sort} onChange={(e) => setSort(e.target.value)}>
                <option value="featured">Featured</option>
                <option value="price_asc">Price: Low to High</option>
                <option value="price_desc">Price: High to Low</option>
                <option value="newest">Newest</option>
                <option value="name_asc">Name: A-Z</option>
              </select>
            </label>
          )}
        </div>

        {error && <p className="text-error page-error">{error}</p>}

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
            {/* Three genuinely different messages, not the same text reused -- an empty
                catalog, "your search matched nothing," and "this category has no
                products" are three different situations, and conflating them would
                mislead whichever one is actually happening. */}
            {searching && <p className="text-muted">Loading...</p>}
            {!searching && products.length === 0 && (
              <p className="text-muted">
                {debouncedQuery
                  ? `No products match "${debouncedQuery}".`
                  : selectedCategory
                    ? `No products in ${selectedCategory} right now.`
                    : "No products available right now."}
              </p>
            )}
            {!searching && products.length > 0 && (
              <div className="product-grid">
                {products.map((product) => (
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
                      <p className="product-card-price">${product.unitPrice.toFixed(2)}</p>
                    </Link>
                    {/* Admin-only -- a regular shopper never sees stock levels, "Out of
                        stock" included. Adding an out-of-stock item to the cart still
                        works with no warning here; the order itself is still rejected
                        at checkout regardless (StockController's reservation logic
                        doesn't trust what this card shows either way). */}
                    {isAdmin && <StockCount quantity={stockByProductId[product.id]} />}
                    <button
                      type="button"
                      className="btn-secondary product-card-add"
                      onClick={() => cart.addItem(product)}
                    >
                      Add to cart
                    </button>
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
