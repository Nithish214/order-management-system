import { createContext, useContext, useEffect, useMemo, useState } from "react";

// Display-only currency conversion -- the actual order/charge always happens in USD
// underneath (Order Service's own prices, and CreateOrderRequest never sends a price at
// all, only productId/quantity -- see CartSummary's handlePlaceOrder), so guessing wrong
// here, or the lookups below failing outright, never risks charging anyone the wrong
// amount. This only changes what number a shopper SEES while browsing.
//
// Two third-party, no-API-key-needed lookups, chained: ipapi.co's IP geolocation for
// "what currency does this visitor's country use" (their IP is visible to any backend
// they talk to anyway; this is the one new place it's shared with a third party), then
// open.er-api.com for the live USD exchange rate for that currency. Both run once, on
// mount, in the background -- the page renders immediately in USD and swaps to the
// detected currency only once (if) both calls succeed, rather than blocking on either.
const CurrencyContext = createContext(null);

const FALLBACK_CURRENCY = "USD";

export function CurrencyProvider({ children }) {
  const [currencyCode, setCurrencyCode] = useState(FALLBACK_CURRENCY);
  const [rate, setRate] = useState(1);

  useEffect(() => {
    let cancelled = false;

    async function detectCurrency() {
      try {
        const geoResponse = await fetch("https://ipapi.co/json/");
        if (!geoResponse.ok) return;
        const geo = await geoResponse.json();
        const detected = geo.currency;
        // No conversion to do, and no point spending the second network call, if the
        // visitor's own currency is already USD.
        if (!detected || detected === FALLBACK_CURRENCY || cancelled) return;

        const rateResponse = await fetch("https://open.er-api.com/v6/latest/USD");
        if (!rateResponse.ok) return;
        const rates = await rateResponse.json();
        const detectedRate = rates.rates?.[detected];
        // Some ISO codes ipapi.co returns (e.g. a few less-common ones) don't have a
        // matching key in this particular rates table -- staying in USD is the correct
        // fallback, not a guess at some other rate.
        if (!detectedRate || cancelled) return;

        setCurrencyCode(detected);
        setRate(detectedRate);
      } catch {
        // Network failure, CORS hiccup, rate-limited free tier -- any of these just
        // leaves the page in USD, which is always a correct (if less convenient) price
        // to show, so there's nothing to surface to the user here.
      }
    }

    detectCurrency();
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo(() => {
    function formatPrice(usdAmount) {
      try {
        return new Intl.NumberFormat(undefined, {
          style: "currency",
          currency: currencyCode,
        }).format(usdAmount * rate);
      } catch {
        // An Intl.NumberFormat construction failure means currencyCode itself is bad --
        // shouldn't happen (it came from open.er-api.com's own rates table above), but
        // plain USD is the safe fallback if it ever does.
        return `$${usdAmount.toFixed(2)}`;
      }
    }

    return { currencyCode, formatPrice };
  }, [currencyCode, rate]);

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  const context = useContext(CurrencyContext);
  if (context === null) {
    throw new Error("useCurrency must be used within a CurrencyProvider");
  }
  return context;
}
