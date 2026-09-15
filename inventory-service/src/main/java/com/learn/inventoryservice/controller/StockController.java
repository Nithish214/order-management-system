package com.learn.inventoryservice.controller;

import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.dto.RestockRequest;
import com.learn.inventoryservice.dto.StockResponse;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.ProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// GET here is the read side of the event flow (watch stock move as order.created events
// land). POST /restock is the only way stock ever goes up -- there is deliberately no way
// to just overwrite a quantity outright, since "receive N more units" is the real-world
// operation (a delivery arrived), not "set the count to some arbitrary number".
//
// Cache-aside on both GET endpoints: check Redis first, fall through to Postgres on a
// miss and populate the cache with what was just read. Namespaced key convention --
// "stock:list" for the whole collection, "stock:{id}" per product -- keeps this service's
// keys distinguishable from anything else that might ever share this Redis instance.
@RestController
@RequestMapping("/stock")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);
    private static final String LIST_KEY = "stock:list";

    private final ProductStockRepository productStockRepository;
    private final StockCache stockCache;

    public StockController(ProductStockRepository productStockRepository, StockCache stockCache) {
        this.productStockRepository = productStockRepository;
        this.stockCache = stockCache;
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStock() {
        List<StockResponse> cached = stockCache.getList(LIST_KEY, StockResponse.class);
        if (cached != null) {
            log.debug("Cache hit: {}", LIST_KEY);
            return ResponseEntity.ok(cached);
        }

        log.debug("Cache miss: {}", LIST_KEY);
        List<StockResponse> stock = productStockRepository.findAll()
                .stream()
                .map(StockResponse::from)
                .toList();
        stockCache.set(LIST_KEY, stock);
        return ResponseEntity.ok(stock);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
        String key = itemKey(productId);
        StockResponse cached = stockCache.get(key, StockResponse.class);
        if (cached != null) {
            log.debug("Cache hit: {}", key);
            return ResponseEntity.ok(cached);
        }

        log.debug("Cache miss: {}", key);
        ProductStock stock = productStockRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("No stock record for product: " + productId));
        StockResponse response = StockResponse.from(stock);
        stockCache.set(key, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/restock")
    @Transactional
    public ResponseEntity<StockResponse> restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request) {
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
        stockCache.delete(itemKey(productId));
        stockCache.delete(LIST_KEY);

        return ResponseEntity.ok(response);
    }

    private String itemKey(Long productId) {
        return "stock:" + productId;
    }
}
