import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { useCurrency } from "../currency/CurrencyContext";
import { friendlyErrorMessage } from "../utils/errors";
import AppHeader from "../components/AppHeader";
import ErrorState from "../components/ErrorState";
import Skeleton from "../components/Skeleton";
import StarRating from "../components/StarRating";
import BarList from "../components/charts/BarList";
import ColumnChart from "../components/charts/ColumnChart";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./AdminDashboardPage.css";

// Below this many units, a product shows up in the low-stock list. Not configurable in the
// UI: it's one admin's rule of thumb for "worth restocking soon," not a setting anyone else
// would need to change per-session.
const LOW_STOCK_THRESHOLD = 10;
const LOW_STOCK_ROWS = 8;

// Same meanings and colors as StatusBadge/the order status page -- an admin who already
// knows "green = confirmed" shouldn't have to learn a second color language here.
const STATUS_META = {
  CONFIRMED: { label: "Confirmed", tone: "success" },
  PENDING: { label: "Pending", tone: "pending" },
  REJECTED: { label: "Rejected", tone: "error" },
  CANCELLED: { label: "Cancelled", tone: "neutral" },
  FAILED: { label: "Failed", tone: "error" },
};

// "2026-09-24" -> a Date at LOCAL midnight. new Date("2026-09-24") would parse as UTC
// midnight and, in any timezone behind UTC, display as the previous day.
function parseDay(isoDate) {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function StatTile({ label, value, note }) {
  return (
    <div className="stat-tile">
      <p className="stat-tile-label text-muted">{label}</p>
      <p className="stat-tile-value">{value}</p>
      {note && <p className="stat-tile-note text-muted">{note}</p>}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <>
      <div className="stat-grid">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="skeleton-stat-tile" />
        ))}
      </div>
      <Skeleton className="skeleton-chart" />
    </>
  );
}

export default function AdminDashboardPage() {
  useDocumentTitle("Dashboard");
  const apiFetch = useApiFetch();
  const { formatPrice, convertPrice, formatAmount, formatCompactAmount } = useCurrency();

  const [summary, setSummary] = useState(null);
  // null = not loaded (or failed -- see stockError); an array once it has been. Kept apart
  // from the summary on purpose: stock comes from a DIFFERENT service (inventory), and a
  // failure there shouldn't blank the sales numbers that loaded fine.
  const [stock, setStock] = useState(null);
  const [stockError, setStockError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      // First load shows the skeleton; a refresh keeps the previous render on screen (dimmed)
      // instead of flashing back to placeholders and jumping the layout.
      if (summary) setRefreshing(true);
      else setLoading(true);
      setError(null);

      const summaryRequest = apiFetch("/analytics/summary").then(async (response) => {
        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message || "Failed to load the dashboard");
        }
        return response.json();
      });
      // Deliberately allowed to fail on its own -- see stockError above.
      const stockRequest = apiFetch("/stock")
        .then(async (response) => {
          if (!response.ok) throw new Error("stock unavailable");
          return response.json();
        })
        .then((rows) => ({ rows }))
        .catch(() => ({ rows: null }));

      try {
        const [summaryData, stockResult] = await Promise.all([summaryRequest, stockRequest]);
        if (cancelled) return;
        setSummary(summaryData);
        setStock(stockResult.rows);
        setStockError(stockResult.rows === null);
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err));
      } finally {
        if (!cancelled) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    }

    load();
    return () => {
      cancelled = true;
    };
    // `summary` is read only to choose between "first load" and "refresh" -- including it in
    // the dependency array would re-run this effect the moment its own result arrived.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiFetch, reloadCount]);

  if (loading && !summary) {
    return (
      <>
        <AppHeader />
        <div className="dashboard-page">
          <h1>Dashboard</h1>
          <DashboardSkeleton />
        </div>
      </>
    );
  }

  if (error && !summary) {
    return (
      <>
        <AppHeader />
        <div className="dashboard-page">
          <ErrorState message={error} onRetry={() => setReloadCount((c) => c + 1)} />
        </div>
      </>
    );
  }

  const { totals, daily, statusBreakdown, topProducts, topRated } = summary;

  const chartData = daily.map((point) => {
    const day = parseDay(point.date);
    return {
      key: point.date,
      label: day.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" }),
      axisLabel: day.toLocaleDateString(undefined, { month: "short", day: "numeric" }),
      // Already in the display currency -- the chart's axis rounding happens in these units.
      value: convertPrice(point.revenue),
      detail: `${point.orders} ${point.orders === 1 ? "order" : "orders"} placed`,
    };
  });

  const lowStock = (stock ?? [])
    .filter((row) => row.availableQuantity < LOW_STOCK_THRESHOLD)
    .sort((a, b) => a.availableQuantity - b.availableQuantity);

  return (
    <>
      <AppHeader />
      <div className={"dashboard-page" + (refreshing ? " dashboard-page--refreshing" : "")}>
        <div className="dashboard-title-row">
          <h1>Dashboard</h1>
          <div className="dashboard-refresh">
            <span className="text-muted">
              Last {summary.windowDays} days · updated {new Date(summary.generatedAt).toLocaleTimeString()}
            </span>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setReloadCount((c) => c + 1)}
              disabled={refreshing}
            >
              Refresh
            </button>
          </div>
        </div>
        {error && <p className="text-error">{error}</p>}

        <div className="stat-grid">
          <StatTile
            label="Revenue"
            value={formatPrice(totals.revenue)}
            note={`from ${totals.ordersConfirmed} confirmed ${totals.ordersConfirmed === 1 ? "order" : "orders"}`}
          />
          <StatTile
            label="Orders placed"
            value={totals.ordersPlaced.toLocaleString()}
            note={
              totals.ordersPlaced > 0
                ? `${Math.round((totals.ordersConfirmed / totals.ordersPlaced) * 100)}% confirmed`
                : undefined
            }
          />
          <StatTile
            label="Average order value"
            value={totals.ordersConfirmed > 0 ? formatPrice(totals.averageOrderValue) : "–"}
            note="confirmed orders only"
          />
          <StatTile
            label="Average rating"
            value={totals.averageRating === null ? "–" : totals.averageRating.toFixed(1)}
            note={`${totals.reviewCount.toLocaleString()} ${totals.reviewCount === 1 ? "review" : "reviews"}, all time`}
          />
        </div>

        <section className="dashboard-section">
          <h2>Revenue per day</h2>
          <p className="text-muted dashboard-section-note">Confirmed orders only. Days with no sales show as a flat mark.</p>
          <ColumnChart
            data={chartData}
            formatValue={formatAmount}
            formatTick={formatCompactAmount}
            ariaLabel={`Revenue per day over the last ${summary.windowDays} days`}
            detailHeading="Orders placed"
            valueHeading="Revenue"
          />
        </section>

        <div className="dashboard-columns">
          <section className="dashboard-section">
            <h2>Orders by status</h2>
            {statusBreakdown.length === 0 ? (
              <p className="text-muted">No orders in this period.</p>
            ) : (
              <BarList
                items={statusBreakdown.map((row) => ({
                  key: row.status,
                  label: STATUS_META[row.status]?.label ?? row.status,
                  value: row.count,
                  valueLabel: row.count.toLocaleString(),
                  tone: STATUS_META[row.status]?.tone ?? "neutral",
                }))}
              />
            )}
          </section>

          <section className="dashboard-section">
            <h2>Top products</h2>
            <p className="text-muted dashboard-section-note">By units sold, confirmed orders.</p>
            {topProducts.length === 0 ? (
              <p className="text-muted">No confirmed sales in this period.</p>
            ) : (
              <BarList
                items={topProducts.map((product) => ({
                  key: product.productId,
                  label: product.name,
                  to: `/products/${product.productId}`,
                  value: product.unitsSold,
                  valueLabel: `${product.unitsSold.toLocaleString()} units`,
                  secondary: formatPrice(product.revenue),
                }))}
              />
            )}
          </section>
        </div>

        <div className="dashboard-columns">
          <section className="dashboard-section">
            <h2>Top rated</h2>
            {topRated.length === 0 ? (
              <p className="text-muted">No reviews yet.</p>
            ) : (
              <ul className="dashboard-list">
                {topRated.map((product) => (
                  <li key={product.productId} className="dashboard-list-row">
                    <Link to={`/products/${product.productId}`} className="dashboard-list-link">
                      {product.name}
                    </Link>
                    <span className="dashboard-rating">
                      <StarRating rating={product.averageRating} size={14} />
                      <span className="dashboard-rating-value">{product.averageRating.toFixed(1)}</span>
                      <span className="text-muted">({product.reviewCount})</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="dashboard-section">
            <h2>Low stock</h2>
            {stockError ? (
              <p className="text-muted">Stock levels are unavailable right now.</p>
            ) : lowStock.length === 0 ? (
              <p className="text-muted">Every product has at least {LOW_STOCK_THRESHOLD} units in stock.</p>
            ) : (
              <>
                <p className="text-muted dashboard-section-note">
                  {lowStock.length} {lowStock.length === 1 ? "product has" : "products have"} fewer than{" "}
                  {LOW_STOCK_THRESHOLD} units.
                </p>
                <ul className="dashboard-list">
                  {lowStock.slice(0, LOW_STOCK_ROWS).map((row) => (
                    <li key={row.productId} className="dashboard-list-row">
                      <Link to={`/products/${row.productId}`} className="dashboard-list-link">
                        {row.sku}
                      </Link>
                      <span className={"dashboard-stock" + (row.availableQuantity === 0 ? " dashboard-stock--out" : "")}>
                        {row.availableQuantity === 0 ? "Out of stock" : `${row.availableQuantity} left`}
                      </span>
                    </li>
                  ))}
                </ul>
                {lowStock.length > LOW_STOCK_ROWS && (
                  <p className="text-muted dashboard-section-note">
                    …and {lowStock.length - LOW_STOCK_ROWS} more.
                  </p>
                )}
              </>
            )}
          </section>
        </div>
      </div>
    </>
  );
}
