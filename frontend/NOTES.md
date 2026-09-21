# Frontend polish pass — notes

Working through the full checklist section by section, committing after each. This file
is updated as it goes; the final version (once every section is done) will summarize
everything built, anything blocked, and what to specifically look at before merging.

## Section 1: First impression

**Landing/login flow decision**: kept login-first, rather than building a separate public
landing page. Reason: `GET /products` already requires an authenticated request at the
Gateway (`api-gateway`'s `SecurityConfig`, `.anyExchange().authenticated()`) — a genuinely
public, pre-login product browse would need a backend/Gateway change, which is out of
scope per the rules for this pass. Instead:
- The login/signup pages now show a real wordmark + one-line tagline ("Browse products,
  add to cart, and check out in minutes.") before the form — this is the actual first
  impression, and previously had zero branding at all.
- The page you land on immediately after logging in (`ProductsPage`) now opens with a
  short intro ("Shop our catalog" / "Browse by category or search to find what you
  need.") instead of a bare "Products" heading, so the storefront identity is
  unmistakable the instant it renders.

**Branding**: introduced a wordmark, "Cartly" — a simple accent-colored square with a
cart glyph + styled text (`components/Logo.jsx`, `components/icons.jsx`). Previously the
nav just showed the plain text "Order Management" with no visual mark at all. This name
is trivially changeable (it's a single string in one file) if you'd rather use something
else — flagging this as a name I chose, not one you specified.

**Favicon**: added `public/favicon.svg`, matching the wordmark exactly (same accent color,
same cart glyph). There was no favicon at all before this — no `public/` folder existed
and `index.html` had no `<link rel="icon">` tag.

**Page titles**: added `hooks/useDocumentTitle.js`, called from every page with what's
actually on screen (a product's own name, "Order #42", the selected category, etc.)
instead of the browser tab being stuck on a static "Order Management System" for the
entire session.

**Bug found and fixed along the way**: `ProductDetailPage` never rendered `<AppHeader />`
at all — visiting any product page lost the entire nav (cart, order history, log out,
and now the branding) with no way back except the browser's own back button or the
page's own small "← Back to products" link. Fixed by wrapping all three of its render
paths (loading, fatal error, and the real product view) in `<AppHeader />`, consistent
with every other page.

## Section 2: Navigation & browsing

Category sidebar's active-state (color + bold) and the working debounced search/category
browsing were already solid — no changes needed there. The cart icon (a real glyph, not
just the word "Cart") was added as part of Section 1's `AppHeader` work.

**Product card hover state**: cards had no hover treatment at all beyond the product
name underlining — added a border-color transition to the accent color on hover
(`ProductsPage.css`), consistent with this design's own "borders over shadows" structure
principle (see `tokens.css`'s comment on that) rather than introducing the one drop
shadow anywhere in the app. Respects `prefers-reduced-motion`.

## Section 3: Checkout flow

Cart page (line items, adjustable quantities, running total, "Place order" CTA) and the
order status page's multi-stage pipeline (already correct for PENDING/CONFIRMED/REJECTED)
were both already solid — no changes needed structurally.

**The one real gap**: checkout success was just a silent `navigate()` to the order status
page, with nothing distinguishing "you just placed this" from "you're revisiting an order
from an hour ago." Fixed by passing `{ state: { justPlaced: true } }` through that
navigation (`CartSummary.jsx`) and showing a genuine "🎉 Order placed!" banner on the
order status page for that one page load — auto-fades after 5s, also dismissable by hand,
respects `prefers-reduced-motion`. Captured once into local state on mount rather than
read fresh on every render, so it doesn't reappear on the page's own 3s status-polling
re-renders.

## Section 4: Every page, no exceptions

**Loading states**: already fully covered (the `Spinner` component, wired into every
page and action button in an earlier session) — no changes needed.

**Error states — the single biggest real gap found in this pass**: every page-level
error was a bare paragraph of red text with no way to recover except a manual browser
reload — worse, on the order status page, a fatal error permanently `clearInterval`'d
its own polling with no way to ever resume short of navigating away and back. Added:
- `components/ErrorState.jsx` — one consistent "message + Try again + Back to products"
  shape, used on `ProductDetailPage`, `OrderStatusPage`, and `OrderHistoryPage`'s fatal
  (nothing else to show) error states.
- A `retryCount` state + dependency-array trigger pattern on each of those pages' load
  effects, plus `ProductsPage`'s own products-fetch effect (which degrades gracefully
  already — the search box and category sidebar stay usable even while products failed
  to load — so it gets a small inline "Try again" next to the error text instead of the
  full `ErrorState` replacing the whole page).
- Fixed a real correctness bug while doing this: none of these effects cleared their own
  `error`/`loading` state at the *start* of a (re)load — meaning a successful retry after
  a failure would leave the old error message on screen (or, on `ProductDetailPage`,
  never clear `loading` correctly) until the new request's own `finally` block ran.

**Empty states**: `CartSummary`'s "Cart is empty" and `OrderHistoryPage`'s "No orders
yet" both now include a "Browse products" link — previously dead ends with nothing to
click. `ProductsPage`'s three empty-search/category/catalog messages were already
distinct and clear (no dead end there — the search box and category sidebar are right
there to change).

**Responsive**: reviewed every page's CSS rather than rewriting anything — the app
already uses `flex-wrap`, `max-width` (never a fixed `width` past mobile viewport
widths), and `aspect-ratio` consistently, with narrow-screen media queries already in
place for the category sidebar and product detail gallery. Nothing found that would
cause horizontal overflow or a broken layout at a phone width. No changes made here.

**No console errors/warnings**: reviewed every list render for a real, unique `key`
(all correct), checked for conditional hook calls (none — every `useDocumentTitle` call
happens unconditionally before any early return), and confirmed `npm run build`/`npm run
lint` stay clean throughout this entire pass. **Caveat, stated plainly**: I don't have a
browser available in this environment, so I could not literally open the app and watch
its live console. Everything above is a careful code-level review, not a substitute for
actually clicking through the flow yourself before merging — please do that specific
check as part of your review.

## Section 5: Final details

**Favicon and page titles**: done in Section 1, since they were tightly coupled to the
branding work there (the default title and the favicon are effectively one "what does
the browser tab look like" concern).

**Transitions**: two added, both subtle, both respecting `prefers-reduced-motion`:
- The product grid now fades in on mount (`product-grid-enter`) instead of popping in
  instantly every time a category switch/search/page change finishes.
- A newly-added cart item now fades and slides in slightly (`cart-item-enter`) instead of
  silently appearing. Deliberately keyed so this only plays for a genuinely NEW item —
  React reconciles existing items by `productId`, so an existing item's quantity
  changing reuses the same DOM node and never replays this.

**Consistent focus/hover states**: found the real inconsistency the checklist called
out — `.category-link`, `.cart-remove`, `.history-order-link`, `.order-item-info`, the
plain nav links, and several others had *no* focus-visible treatment at all, while
`.btn-primary`/`.btn-secondary`/`.detail-thumb` each had their own separately-declared
(but identical) one. Replaced all of that with a single global rule in `index.css`
(`a:focus-visible, button:focus-visible`) covering every link and button in the app,
including ones that never had a focus ring before, and removed the two now-redundant
duplicates. Also swept every hardcoded color value in the codebase (`grep`) to confirm
none were an inconsistency — all of them are the same deliberate "white text on a solid
accent/status background" or "black video letterboxing" pattern already used
consistently, not a page going its own way.

---

## Summary

Everything in the checklist is genuinely done, with one exception logged below as
blocked. Five commits, one per checklist section, each preceded by a clean
`npm run build` and `npm run lint`.

**Self-review pass**: walked the checklist item by item after finishing Section 5.
Found one real gap: `NotFoundPage` had no branding at all (unlike the auth pages, which
now show the plain `Logo` even without the full nav) — someone landing on a dead link
would see a page that looked like it belonged to a different, unbranded site. Fixed by
adding the same plain `Logo` there, consistent with the auth pages' pattern (still no
full `AppHeader`/nav, deliberately, since this route is reachable while logged out —
see that page's own comment).

**What was built**: a real wordmark + favicon + dynamic page titles (there was none of
this before); a login-first flow with actual storefront framing on both the login page
and the immediate post-login landing, since a true pre-login public catalog isn't
possible without a backend change; product card hover states; a genuine "Order placed!"
confirmation moment instead of a silent redirect; a reusable `ErrorState` component with
real retry, replacing five different dead-end error messages (one of which,
`OrderStatusPage`, permanently stopped its own polling on any failure with no way to
recover short of leaving and coming back); "browse products" CTAs on the two empty
states that used to be dead ends; subtle mount transitions on the product grid and new
cart items; and one consistent global focus-visible treatment replacing an inconsistent
patchwork where most custom links/buttons had no keyboard focus indicator at all.

**Real bug fixed along the way (not on the original checklist)**: `ProductDetailPage`
never rendered `<AppHeader />` at all — visiting any single product page lost the entire
nav, cart access, and (now) branding, with no way back except the browser's own back
button.

**Blocked, needs backend**: none of the checklist items themselves are blocked. The one
thing worth flagging as a deliberate scope boundary, not a blocker: a true public,
pre-login product catalog would need `api-gateway`'s `SecurityConfig` to allow
unauthenticated `GET /products` — currently `.anyExchange().authenticated()` requires a
token for everything. I did not touch this (it's a backend/API-contract change, out of
scope per the rules), and instead made the login-first flow itself feel intentional
(see Section 1). If a public catalog is something you actually want, that's the specific
change it would take.

**What to specifically look at before merging**:
1. The "Cartly" name — I picked it since the checklist explicitly asked for a real
   wordmark rather than the literal product name; it's a one-line change in `Logo.jsx`
   if you'd rather use something else.
2. Actually click through the full flow in a real browser and watch the console — I
   don't have one available in this environment, so everything here is a careful
   code-level review, not a substitute for that.
3. Resize the window down to a phone width and confirm it feels right — the existing
   `flex-wrap`/`max-width` patterns looked sound in review, but I couldn't visually
   confirm the result.

---

# Round 2 (branch: frontend-polish-2)

Continuing on a new branch, building pure-frontend features on top of everything above.
Working through each step, stopping after each for review, per the instructions for
this round.

## Step 1: Sort + filter on the product grid

**What changed**: `ProductsPage`'s existing sort dropdown (Featured / Price low-high /
Price high-low / Newest / Name A-Z) used to send a `sort` query param to the backend and
trigger a fresh fetch on every change. It's now applied **entirely client-side**, to
whatever page of products is already loaded — picking a different sort no longer
touches the network at all. Added a min/max price range filter (two plain number
inputs, USD) alongside it, combining correctly with sort and with the existing category
selection (both operate on the same already-fetched list). A "Reset" action appears
only when a non-default sort or price filter is actually active, clearing both at once.

**"Newest" without a real date field**: `ProductResponse` never sends a `createdAt` to
the frontend — but `id` is assigned in insertion order, so sorting by `id` descending is
exactly "most recently added," client-side, with no backend change. Not a guess or a
fake stand-in — the same ordering the backend's own "newest" sort was almost certainly
already doing internally.

**Visual treatment**: the sort `<select>` and both price inputs get an accent-colored
border/text when they hold a non-default value — the same "accent color means currently
applied" language `.category-link-active` already uses elsewhere on this exact page,
not a new badge/pill invented for this. The "Reset" actions reuse the existing
`.inline-retry` button-styled-as-a-link class from the earlier polish pass, for the same
reason.

**Flagging per rule 4, even though scoped correctly per your instructions**: because
this only ever sorts/filters the *current page* (up to 100 products, this app's page
size), a category with more than one page of results will only have sort/price-filter
applied within whichever page you're currently viewing — paging to the next page shows
that page in the server's own default order, unaffected by whatever sort/filter was
active on the previous page. This matches exactly what was asked ("applied client-side
to the already-fetched product list... no new API calls needed"), so I implemented it
as specified rather than silently doing something bigger — but genuinely correct
sorting/filtering across an ENTIRE multi-page category would need the backend's
`/products` and `/products/search` endpoints to accept a price range, and to keep doing
the sorting itself (the way the old server-side `sort` param already did) rather than
the frontend re-sorting only what it happens to have in memory. Flagging this now so
it's a known, visible tradeoff rather than something you discover from a bug report.

## Step 2: Toast notifications

**What was built**: `toast/ToastContext.jsx` + `toast/Toast.css` -- a small Provider +
`useToast()` hook, no library. Toasts stack bottom-center, auto-dismiss after 3s, are
also dismissable by hand, and visually reuse the exact "tinted background + solid
colored border" language `OrderStatusPage`'s `.outcome`/`.order-placed-banner` already
established, rather than inventing a new "toast library" look.

**Where it's wired up**: directly inside `CartContext`'s `addItem`/`setQuantity`/
`removeItem` (only on their success branch — a failed cart action never claims to have
succeeded), not repeated at each call site — every page that touches the cart (the
product grid, the product detail page, the cart page itself) gets the toast automatically,
with nothing to remember to add per-page. Specifically:
- `addItem` success → "Added to cart"
- `removeItem`, or `setQuantity` down to `<= 0` (this app's existing "remove via
  quantity" convention) → "Removed from cart"
- A quantity *tick* (the stepper's plain +/- buttons) deliberately does **not** toast —
  only asked for "added"/"removed," and toasting every single click would just train
  people to tune the toasts out.
- `clear()` (called right after a successful checkout) deliberately does **not** toast
  either — that moment already gets its own, much more prominent "Order placed!" banner
  (see Round 1's Step 3); a second toast at the same instant would compete with it over
  something the customer didn't even directly do.

**The one other genuinely silent success moment found**: admin's "Restock" button on
`ProductDetailPage` — success only changed a plain number (the stock count) sitting
among several other page elements, easy to miss. Now also shows a toast
("Restocked N -- now M in stock"). Deliberately did **not** add toasts to image/video
upload or removal, since those already have their own obvious, unambiguous result (a
new thumbnail appears, a slide disappears) — adding a toast on top would be the exact
toast-overuse this step's own instructions warned against, not genuinely new feedback.

**Not added**: error-variant toasts. Every failure path in this app already shows an
inline error message somewhere on the page (see Round 1's `ErrorState` work and the
various `text-error` paragraphs) — a duplicate toast for the same failure would be two
error indicators competing for one problem, not an improvement. The toast system fully
supports an `"error"` type (same subtle-background/solid-border treatment, just the red
tokens instead of green) for if a genuinely toast-shaped failure ever comes up, but
nothing currently calls it with that type.
