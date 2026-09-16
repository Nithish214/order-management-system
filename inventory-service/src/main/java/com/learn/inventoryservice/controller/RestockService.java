package com.learn.inventoryservice.controller;

import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.dto.RestockRequest;
import com.learn.inventoryservice.dto.StockResponse;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.ProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// One restock "attempt" for one product, pulled out of StockController for the same reason
// OrderCreatedListener's and ReservationReleaser's actual work lives in their own service
// beans: StockController.restock() needs to call this fresh, through this bean's own Spring
// proxy, on every retry, so a version conflict gets a genuinely new transaction and a fresh
// read on the next attempt rather than reusing a persistence context that already saw (and
// lost against) a stale version.
@Service
public class RestockService {

    private final ProductStockRepository productStockRepository;
    private final StockCache stockCache;

    public RestockService(ProductStockRepository productStockRepository, StockCache stockCache) {
        this.productStockRepository = productStockRepository;
        this.stockCache = stockCache;
    }

    @Transactional
    public StockResponse applyRestock(Long productId, RestockRequest request) {
        ProductStock stock = productStockRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("No stock record for product: " + productId));
        stock.setAvailableQuantity(stock.getAvailableQuantity() + request.getQuantity());
        StockResponse response = StockResponse.from(stock);

        // Invalidate both keys this change affects -- the item itself, and the list that
        // embeds it. Deletes rather than re-populates (see StockCache#delete for why); the
        // small window between this delete and the transaction's actual commit (a moment
        // later, once this method returns and Spring's @Transactional proxy commits) is an
        // accepted tradeoff here, not worth the added complexity of a proper post-commit
        // hook for a single-writer admin action like restocking.
        stockCache.delete(StockCache.itemKey(productId));
        stockCache.delete(StockCache.LIST_KEY);

        return response;
    }
}
