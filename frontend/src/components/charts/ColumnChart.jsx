import { useState } from "react";
import "./ColumnChart.css";

const TICK_TARGET = 4;
// The peak label is centered on its bar; this many columns at each end anchor it to that
// edge instead, so it can't spill outside the plot.
const EDGE_COLUMNS = 3;

// Rounds the axis up to a "clean" top (0 / 500 / 1,000 ...) instead of stopping at the raw
// maximum, so the gridlines land on numbers a person would actually say out loud.
function niceScale(max) {
  if (max <= 0) return { top: 1, step: 0.25 };
  const rawStep = max / TICK_TARGET;
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const residual = rawStep / magnitude;
  const niceResidual = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 2.5 ? 2.5 : residual <= 5 ? 5 : 10;
  const step = niceResidual * magnitude;
  return { top: Math.ceil(max / step) * step, step };
}

// One series, one hue (the accent) -- so no legend box: the chart's own heading already
// says what's plotted. Bars are the hit targets (the whole column slot, not just the painted
// bar), a tooltip follows the pointer OR the arrow keys, and every value is also reachable
// without either through the "View as table" twin below -- the tooltip never gates a number.
//
// data: [{ key, label, axisLabel, value, detail }] -- label is the full text a tooltip and
// screen reader use ("Thu, Sep 24"), axisLabel the short form for the x-axis.
export default function ColumnChart({ data, formatValue, formatTick, ariaLabel, valueHeading, detailHeading }) {
  const [activeIndex, setActiveIndex] = useState(null);

  const count = data.length;
  const max = Math.max(0, ...data.map((point) => point.value));
  const { top, step } = niceScale(max);
  // An empty chart gets just the zero line -- invented "$0.25 / $0.50" gridlines over
  // nothing would imply there was a scale to read.
  const ticks = [];
  for (let value = 0; value <= (max <= 0 ? 0 : top + step / 1000); value += step) ticks.push(value);
  const peakIndex = max > 0 ? data.findIndex((point) => point.value === max) : -1;
  const active = activeIndex === null ? null : data[activeIndex];

  function indexFromPointer(event) {
    const rect = event.currentTarget.getBoundingClientRect();
    const fraction = (event.clientX - rect.left) / rect.width;
    return Math.min(count - 1, Math.max(0, Math.floor(fraction * count)));
  }

  function handleKeyDown(event) {
    const last = count - 1;
    const keys = {
      ArrowLeft: () => Math.max(0, (activeIndex ?? last + 1) - 1),
      ArrowRight: () => Math.min(last, (activeIndex ?? -1) + 1),
      Home: () => 0,
      End: () => last,
    };
    if (!keys[event.key]) return;
    event.preventDefault();
    setActiveIndex(keys[event.key]());
  }

  function edgeClass(index) {
    if (index < EDGE_COLUMNS) return "chart-anchor--start";
    if (index >= count - EDGE_COLUMNS) return "chart-anchor--end";
    return "chart-anchor--center";
  }

  return (
    <figure className="chart">
      <div className="chart-body">
        <div className="chart-yaxis" aria-hidden="true">
          {ticks.map((tick) => (
            <span key={tick} className="chart-tick" style={{ bottom: `${(tick / top) * 100}%` }}>
              {formatTick(tick)}
            </span>
          ))}
        </div>

        <div
          className="chart-plot"
          role="group"
          tabIndex={0}
          aria-label={`${ariaLabel}. Use the left and right arrow keys to inspect each day, or open the table below.`}
          onPointerMove={(event) => setActiveIndex(indexFromPointer(event))}
          onPointerDown={(event) => setActiveIndex(indexFromPointer(event))}
          // Touch keeps the last-touched bar selected after the finger lifts (there's no
          // hover to return to); only a real mouse leaving clears it.
          onPointerLeave={(event) => event.pointerType === "mouse" && setActiveIndex(null)}
          onBlur={() => setActiveIndex(null)}
          onKeyDown={handleKeyDown}
        >
          <div className="chart-gridlines" aria-hidden="true">
            {ticks.map((tick) => (
              <div key={tick} className="chart-gridline" style={{ bottom: `${(tick / top) * 100}%` }} />
            ))}
          </div>

          <div className="chart-columns" aria-hidden="true">
            {data.map((point, index) => {
              const heightPercent = (point.value / top) * 100;
              const isPeak = index === peakIndex && activeIndex === null;
              return (
                <div key={point.key} className="chart-col">
                  <div
                    className={
                      "chart-bar" +
                      (point.value <= 0 ? " chart-bar--zero" : "") +
                      (index === activeIndex ? " chart-bar--active" : "")
                    }
                    style={point.value > 0 ? { height: `${heightPercent}%` } : undefined}
                  />
                  {isPeak && (
                    <span className={`chart-peak-label ${edgeClass(index)}`} style={{ bottom: `calc(${heightPercent}% + 4px)` }}>
                      {formatTick(point.value)}
                    </span>
                  )}
                </div>
              );
            })}
          </div>

          {max === 0 && <p className="chart-empty text-muted">Nothing to plot in this period.</p>}

          {active && (
            <div
              className="chart-tooltip"
              // Beside the bar, never on top of it: a tooltip centered above a tall bar
              // covers the very thing being inspected. Left half of the chart -> tooltip to
              // the right of the bar; right half -> to its left, so it always has room.
              style={
                activeIndex < count / 2
                  ? { left: `calc(${((activeIndex + 1) / count) * 100}% + 8px)` }
                  : { right: `calc(${(1 - activeIndex / count) * 100}% + 8px)` }
              }
              role="status"
            >
              <strong className="chart-tooltip-value">{formatValue(active.value)}</strong>
              <span className="chart-tooltip-label">{active.label}</span>
              {active.detail && <span className="chart-tooltip-label">{active.detail}</span>}
            </div>
          )}
        </div>
      </div>

      <div className="chart-xaxis" aria-hidden="true">
        <span>{data[0]?.axisLabel}</span>
        <span>{data[Math.floor((count - 1) / 2)]?.axisLabel}</span>
        <span>{data[count - 1]?.axisLabel}</span>
      </div>

      <details className="chart-table">
        <summary>View as table</summary>
        <div className="chart-table-scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">{detailHeading}</th>
                <th scope="col">{valueHeading}</th>
              </tr>
            </thead>
            <tbody>
              {data.map((point) => (
                <tr key={point.key}>
                  <th scope="row">{point.label}</th>
                  <td>{point.detail}</td>
                  <td>{formatValue(point.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
