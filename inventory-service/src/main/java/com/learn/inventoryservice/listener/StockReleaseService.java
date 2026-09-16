package com.learn.inventoryservice.listener;

import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.entity.OrderReservationItem;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.OrderReservationItemRepository;
import com.learn.inventoryservice.repository.ProductStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// One release "attempt" for one order -- everything ReservationReleaser used to do inline.
// REQUIRES_NEW (not the default REQUIRED) matters here specifically: ReservationReleaser.
// release() is always called from *inside* a caller that's already in its own transaction
// (PaymentFailedListener/OrderCancelledListener are both @Transactional). If this attempt
// used the default propagation, it would just join that already-open transaction and share
// its persistence context -- so retrying by calling this method again would still be reusing
// the exact same (by then version-conflicted) entities already sitting in that context,
// not a fresh read. REQUIRES_NEW suspends the caller's transaction for the duration of this
// call and gives this attempt its own transaction and its own persistence context, so a
// retry's findById() genuinely goes back to the database rather than to a cache of what we
// already know is stale.
@Service
public class StockReleaseService {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseService.class);

    private final OrderReservationItemRepository orderReservationItemRepository;
    private final ProductStockRepository productStockRepository;
    private final StockCache stockCache;

    public StockReleaseService(
            OrderReservationItemRepository orderReservationItemRepository,
            ProductStockRepository productStockRepository,
            StockCache stockCache
    ) {
        this.orderReservationItemRepository = orderReservationItemRepository;
        this.productStockRepository = productStockRepository;
        this.stockCache = stockCache;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAttempt(Long orderId) {
        List<OrderReservationItem> reserved = orderReservationItemRepository.findByOrderId(orderId);

        if (reserved.isEmpty()) {
            log.info("No reservation found for order {} -- nothing to release", orderId);
            return;
        }

        for (OrderReservationItem item : reserved) {
            ProductStock stock = productStockRepository.findById(item.getProductId()).orElse(null);
            if (stock != null) {
                // Same as the reservation side: a version conflict here means some other
                // transaction touched this exact row since the findById() a few lines up,
                // and this whole method's commit throws instead of silently overwriting it.
                stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());
            }
            stockCache.delete(StockCache.itemKey(item.getProductId()));
        }
        stockCache.delete(StockCache.LIST_KEY);
        orderReservationItemRepository.deleteByOrderId(orderId);
        log.info("Released reserved stock for order {}", orderId);
    }
}
