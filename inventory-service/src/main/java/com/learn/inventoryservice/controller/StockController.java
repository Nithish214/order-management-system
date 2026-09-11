package com.learn.inventoryservice.controller;

import com.learn.inventoryservice.dto.RestockRequest;
import com.learn.inventoryservice.dto.StockResponse;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.repository.ProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/stock")
public class StockController {

    private final ProductStockRepository productStockRepository;

    public StockController(ProductStockRepository productStockRepository) {
        this.productStockRepository = productStockRepository;
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStock() {
        List<StockResponse> stock = productStockRepository.findAll()
                .stream()
                .map(StockResponse::from)
                .toList();
        return ResponseEntity.ok(stock);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
        ProductStock stock = productStockRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("No stock record for product: " + productId));
        return ResponseEntity.ok(StockResponse.from(stock));
    }

    @PostMapping("/{productId}/restock")
    @Transactional
    public ResponseEntity<StockResponse> restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request) {
        ProductStock stock = productStockRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("No stock record for product: " + productId));
        stock.setAvailableQuantity(stock.getAvailableQuantity() + request.getQuantity());
        return ResponseEntity.ok(StockResponse.from(stock));
    }
}
