package com.learn.inventoryservice.listener;

import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.entity.OrderReservationItem;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.OrderReservationItemRepository;
import com.learn.inventoryservice.repository.ProductStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

// Credits back whatever this service actually reserved for an order (via
// OrderReservationItem), then deletes those rows -- they're no longer active
// reservations. Shared by OrderCancelledListener (order.cancelled) and the new
// PaymentFailedListener (payment.failed): both mean the exact same thing to this service
// -- "this order isn't happening any more, give the stock back" -- they just arrive from
// two different sources, so the release logic itself shouldn't be duplicated.
@Component
public class ReservationReleaser {

    private static final Logger log = LoggerFactory.getLogger(ReservationReleaser.class);

    private final OrderReservationItemRepository orderReservationItemRepository;
    private final ProductStockRepository productStockRepository;
    private final StockCache stockCache;

    public ReservationReleaser(
            OrderReservationItemRepository orderReservationItemRepository,
            ProductStockRepository productStockRepository,
            StockCache stockCache
    ) {
        this.orderReservationItemRepository = orderReservationItemRepository;
        this.productStockRepository = productStockRepository;
        this.stockCache = stockCache;
    }

    public void release(Long orderId) {
        List<OrderReservationItem> reserved = orderReservationItemRepository.findByOrderId(orderId);

        if (reserved.isEmpty()) {
            // Nothing was ever reserved for this order (rejected for stock before payment
            // was ever attempted, or already released by the other caller) -- correctly
            // nothing to release.
            log.info("No reservation found for order {} -- nothing to release", orderId);
            return;
        }

        for (OrderReservationItem item : reserved) {
            ProductStock stock = productStockRepository.findById(item.getProductId()).orElse(null);
            if (stock != null) {
                stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());
            }
            // Without this, a cached stock:{id}/stock:list would stay stale after this
            // release put stock back -- same reasoning as every other write path to
            // product_stock.
            stockCache.delete(StockCache.itemKey(item.getProductId()));
        }
        stockCache.delete(StockCache.LIST_KEY);
        orderReservationItemRepository.deleteByOrderId(orderId);
        log.info("Released reserved stock for order {}", orderId);
    }
}
