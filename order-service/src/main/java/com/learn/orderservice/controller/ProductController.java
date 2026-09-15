package com.learn.orderservice.controller;

import com.learn.orderservice.dto.ImageUploadUrlRequest;
import com.learn.orderservice.dto.ImageUploadUrlResponse;
import com.learn.orderservice.dto.ProductResponse;
import com.learn.orderservice.dto.SetProductImageRequest;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.repository.ProductRepository;
import com.learn.orderservice.service.ProductImageUploadService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only lookup so a client (Postman/Swagger, no frontend yet) can discover valid
// productIds and prices before calling POST /orders. Stock is no longer tracked here at
// all -- see Inventory Service's GET /stock for the live, authoritative quantity.
//
// The two image endpoints below carry no auth check of their own -- same trust model as
// every other endpoint in this service (and Inventory Service's restock endpoint): the
// Gateway is the one place that enforces the "admin" group requirement (see
// api-gateway's SecurityConfig), this service just trusts that a request reaching it at
// all has already been authorized.
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductImageUploadService productImageUploadService;

    public ProductController(ProductRepository productRepository, ProductImageUploadService productImageUploadService) {
        this.productRepository = productRepository;
        this.productImageUploadService = productImageUploadService;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    // Step 1 of the upload flow: hands back a short-lived S3 URL the browser will PUT the
    // actual file to directly (see ProductImageUploadService) -- this call itself never
    // sees any file bytes, it just proves the product exists before minting a URL for it.
    @PostMapping("/{id}/image-upload-url")
    public ResponseEntity<ImageUploadUrlResponse> createImageUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        ImageUploadUrlResponse response = productImageUploadService.createUploadUrl(product.getId(), request.getContentType());
        return ResponseEntity.ok(response);
    }

    // Step 2: called only after the browser's direct PUT to S3 has already succeeded --
    // this just records the final URL against the product, it never touches file bytes.
    @PutMapping("/{id}/image")
    @Transactional
    public ResponseEntity<ProductResponse> setProductImage(
            @PathVariable Long id,
            @Valid @RequestBody SetProductImageRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        product.setImageUrl(request.getImageUrl());
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}
