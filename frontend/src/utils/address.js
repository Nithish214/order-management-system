// Shared by every place an address is shown (address book, checkout picker, order status and
// order history) so they can't drift into four slightly different formats.

// ["1 Home Street", "Flat 2", "London, England SW1A 1AA", "United Kingdom"] -- line2 omitted
// when there isn't one.
export function formatAddressLines(address) {
  return [
    address.line1,
    address.line2,
    `${address.city}, ${address.state} ${address.postalCode}`,
    address.country,
  ].filter(Boolean);
}

// One line, for tight spaces (an order-history row).
export function formatAddressOneLine(address) {
  return formatAddressLines(address).join(", ");
}
