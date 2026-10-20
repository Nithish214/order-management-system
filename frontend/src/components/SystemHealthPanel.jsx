import { useEffect, useRef, useState } from "react";
import { useApiFetch } from "../api/useApiFetch";
import ColumnChart from "./charts/ColumnChart";
import "./SystemHealthPanel.css";

// Friendlier than the raw service names GET /system/health uses (which match this
// project's own package/container names, e.g. "order-service") -- an admin reading a
// dashboard shouldn't have to know those are the same thing.
const SERVICE_LABELS = {
  "api-gateway": "API Gateway",
  "order-service": "Order Service",
  "inventory-service": "Inventory Service",
  "payment-service": "Payment Service",
};

// GET /system/health's own fixed polling cadence (SystemHealthSampler, api-gateway) --
// polling faster than the server actually samples would just mean re-rendering with
// data that hasn't changed.
const POLL_INTERVAL_MS = 5000;

// "free"/"total"/"threshold" inside diskSpace's own details arrive as raw byte counts
// (a string, like every other detail value -- see SystemHealthSampler's flattenComponents)
// -- genuinely unreadable as-is ("509722226688" means nothing at a glance). Everything
// else (a database product name, a Redis version string) is already human-sized text,
// left untouched.
const BYTE_KEYS = new Set(["free", "total", "threshold"]);

function formatDetailValue(key, value) {
  if (BYTE_KEYS.has(key) && /^\d+$/.test(value)) {
    return `${(Number(value) / 1024 ** 3).toFixed(1)} GB`;
  }
  return value;
}

function ServiceCard({ service }) {
  const label = SERVICE_LABELS[service.name] ?? service.name;
  const isUp = service.status === "UP";
  const components = Object.entries(service.componentDetails ?? {});

  const chartData = service.history.map((point) => {
    const time = new Date(point.timestamp);
    return {
      key: point.timestamp,
      label: time.toLocaleTimeString(),
      axisLabel: time.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" }),
      value: point.requestsPerSecond,
    };
  });

  return (
    <div className="system-health-card">
      <div className="system-health-card-header">
        <h3>{label}</h3>
        <span className={"system-health-status" + (isUp ? " system-health-status--up" : " system-health-status--down")}>
          {service.status}
        </span>
      </div>

      <p className="system-health-rps">
        <strong>{service.requestsPerSecond.toFixed(2)}</strong>
        <span className="text-muted"> req/s right now</span>
      </p>

      <ColumnChart
        data={chartData}
        formatValue={(v) => `${v.toFixed(2)} req/s`}
        formatTick={(v) => v.toFixed(1)}
        ariaLabel={`Requests per second for ${label} over the last 5 minutes`}
        valueHeading="Requests/sec"
        detailHeading="Time"
      />

      {/* Only once there's real health-indicator detail -- api-gateway's own entry has
          none (see SystemHealthSampler#sampleSelf: no HTTP round trip to itself, so
          nothing to parse), same "absent means none, not an empty heading" reasoning as
          the product detail page's description/specs sections. */}
      {components.length > 0 && (
        <dl className="system-health-components">
          {components.map(([name, component]) => (
            <div className="system-health-component" key={name}>
              <dt>
                {name}
                <span
                  className={
                    "system-health-status system-health-status--small" +
                    (component.status === "UP" ? " system-health-status--up" : " system-health-status--down")
                  }
                >
                  {component.status}
                </span>
              </dt>
              {component.details && (
                <dd>
                  {Object.entries(component.details)
                    .map(([key, value]) => `${key}: ${formatDetailValue(key, value)}`)
                    .join(" · ")}
                </dd>
              )}
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

// Live, auto-refreshing -- deliberately separate from the rest of AdminDashboardPage's
// load/refresh cycle (a single fetch of a 30-day window, refreshed only by hand). This
// polls on its own fixed interval the whole time this panel is mounted, independent of
// whether someone's sitting on the business-metrics half of the page or not.
export default function SystemHealthPanel() {
  const apiFetch = useApiFetch();
  const [services, setServices] = useState(null);
  const [error, setError] = useState(null);
  // Tracks whether a request is already in flight, across renders -- a ref, not state,
  // since it's never read by the render itself, only by the poll loop to decide whether
  // to skip a tick (a slow response shouldn't pile up a second overlapping request 5s
  // later).
  const fetchInFlight = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      if (fetchInFlight.current) return;
      fetchInFlight.current = true;
      try {
        const response = await apiFetch("/system/health");
        if (!response.ok) throw new Error("System health is unavailable right now");
        const data = await response.json();
        if (!cancelled) {
          setServices(data);
          setError(null);
        }
      } catch {
        // Keeps whatever was last shown on screen rather than blanking it -- a single
        // missed poll (a transient network blip) shouldn't make an otherwise-healthy
        // dashboard look broken. The error line below only shows once there's nothing
        // successful to fall back on yet.
        if (!cancelled) setError("Could not load system health.");
      } finally {
        fetchInFlight.current = false;
      }
    }

    poll();
    const intervalId = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [apiFetch]);

  return (
    <section className="dashboard-section">
      <h2>System health</h2>
      <p className="text-muted dashboard-section-note">
        Live, updated every {POLL_INTERVAL_MS / 1000}s. Requests/sec excludes this panel's own polling traffic.
      </p>
      {error && !services && <p className="text-error">{error}</p>}
      {services && (
        <div className="system-health-grid">
          {services.map((service) => (
            <ServiceCard key={service.name} service={service} />
          ))}
        </div>
      )}
    </section>
  );
}
