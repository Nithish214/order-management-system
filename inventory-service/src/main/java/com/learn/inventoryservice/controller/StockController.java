package com.learn.inventoryservice.controller;

import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.dto.RestockRequest;
import com.learn.inventoryservice.dto.StockResponse;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.ProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    // Same bounded-retry reasoning as the two Kafka listeners (OrderCreatedListener,
    // ReservationReleaser): this is now a third writer to product_stock's versioned rows,
    // and an admin clicking "Restock" the same moment an order reserves the last few units
    // of that product is exactly the kind of brief, incidental conflict @Version exists to
    // catch rather than silently lose.
    private static final int MAX_ATTEMPTS = 3;

    private final ProductStockRepository productStockRepository;
    private final StockCache stockCache;
    private final RestockService restockService;

    public StockController(ProductStockRepository productStockRepository, StockCache stockCache,
                            RestockService restockService) {
        this.productStockRepository = productStockRepository;
        this.stockCache = stockCache;
        this.restockService = restockService;
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStock() {
        List<StockResponse> cached = stockCache.getList(StockCache.LIST_KEY, StockResponse.class);
        if (cached != null) {
            log.debug("Cache hit: {}", StockCache.LIST_KEY);
            return ResponseEntity.ok(cached);
        }

        log.debug("Cache miss: {}", StockCache.LIST_KEY);
        List<StockResponse> stock = productStockRepository.findAll()
                .stream()
                .map(StockResponse::from)
                .toList();
        stockCache.set(StockCache.LIST_KEY, stock);
        return ResponseEntity.ok(stock);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
        String key = StockCache.itemKey(productId);
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

    // Deliberately calls out to RestockService.applyRestock() -- a separate bean -- rather
    // than keeping that logic as a private method here. Retrying a same-class @Transactional
    // method via a plain internal call bypasses Spring's proxy entirely (self-invocation),
    // which would silently mean NO transaction at all on the retried attempts, not just a
    // reused one -- see RestockService's comment.
    @PostMapping("/{productId}/restock")
    public ResponseEntity<StockResponse> restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return ResponseEntity.ok(restockService.applyRestock(productId, request));
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Product {}: still conflicting after {} attempts restocking", productId, attempt, ex);
                    // A genuine, if rare, failure to surface to the admin -- 409 Conflict is
                    // the correct status for "the resource changed under you, try again",
                    // and the retry already absorbed the case where trying again immediately
                    // would have helped.
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
                log.info("Product {}: optimistic lock conflict restocking on attempt {}/{} -- retrying",
                        productId, attempt, MAX_ATTEMPTS);
            }
        }
        // Unreachable: the loop above always returns or throws by the time attempt ==
        // MAX_ATTEMPTS, but the compiler can't see that.
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
