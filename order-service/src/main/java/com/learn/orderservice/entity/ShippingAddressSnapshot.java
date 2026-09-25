package com.learn.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// The address an order was actually shipped to, copied onto the order at placement time --
// the same idea as OrderItem#unitPrice (freeze what was true then, don't point at something
// that can change), applied to an address.
//
// @Embeddable = flattened ship_* columns ON the orders table, still a real object in code
// (order.getShippingAddress().getCity()). The alternative was a separate one-row-per-order
// order_shipping_address table. The tradeoff, for THIS system:
//
//   Flattened columns (chosen)
//     + Written in the same INSERT as the order -- nothing to forget, no second row that could
//       be missing or orphaned, and an order without its address can't exist half-saved.
//     + Read with the order: no extra join and no extra lazy collection. The order queries
//       here already fetch-join items and products; another association would add to that.
//     + Immutable by nature -- there's no UPDATE path at all, so nothing to guard.
//     - orders gets six more columns, NULL for every pre-existing order.
//     - A second address per order (say a billing address) would mean another set of columns.
//
//   Separate table
//     + orders stays narrow; legacy orders simply have no row, no NULL columns at all.
//     + Extends cleanly to several addresses per order (billing/shipping via a type column)
//       or to versioning/auditing.
//     - An extra join or lazy load on every order read (N+1 risk on the history list), and a
//       second insert that must succeed together with the first.
//
// One address, always read together with its order, never edited, is the flattened case. If
// billing addresses ever show up, that's the moment to move to the separate table.
//
// The user's private label ("Home", "Work") is deliberately NOT copied: it's a nickname for
// their own address book, not part of where a parcel goes.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressSnapshot {

    @Column(name = "ship_line1")
    private String line1;

    @Column(name = "ship_line2")
    private String line2;

    @Column(name = "ship_city")
    private String city;

    @Column(name = "ship_state")
    private String state;

    @Column(name = "ship_postal_code")
    private String postalCode;

    @Column(name = "ship_country")
    private String country;
}
