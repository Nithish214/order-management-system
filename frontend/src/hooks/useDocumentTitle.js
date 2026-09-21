import { useEffect } from "react";

// Every page calls this with whatever's actually on screen ("Products", a specific
// product's own name, "Order #42") instead of leaving the browser tab stuck on
// index.html's static default for the entire session -- the tab title is one of the
// first things a returning visitor (or someone with several tabs open) actually looks at
// to tell pages apart.
export function useDocumentTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · Cartly` : "Cartly";
    // Deliberately no cleanup restoring the previous title -- the next page's own call
    // to this same hook is what sets the next title; there's no moment where "nothing"
    // should be showing in between.
  }, [title]);
}
