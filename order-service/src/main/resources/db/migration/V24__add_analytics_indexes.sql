-- Backs the admin dashboard's aggregates (AnalyticsRepository): every query there filters
-- orders by a recent created_at window, then joins order_item by order_id. Neither column
-- had an index -- fine while these tables were small, but the load tests have since
-- generated tens of thousands of orders, and a full scan per dashboard refresh is wasted work
-- on the smallest box in the deployment. Also helps the existing per-user order-history
-- fetch, which joins order_item the same way.
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_order_item_order_id ON order_item(order_id);
