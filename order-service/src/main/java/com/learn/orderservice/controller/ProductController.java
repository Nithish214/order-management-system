package com.learn.orderservice.controller;

import com.learn.orderservice.dto.AddProductImageRequest;
import com.learn.orderservice.dto.ImageUploadUrlRequest;
import com.learn.orderservice.dto.ImageUploadUrlResponse;
import com.learn.orderservice.dto.ProductResponse;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.entity.ProductImage;
import com.learn.orderservice.repository.ProductImageRepository;
import com.learn.orderservice.repository.ProductRepository;
import com.learn.orderservice.service.ProductImageUploadService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only lookup so a client (Postman/Swagger, no frontend yet) can discover valid
// productIds and prices before calling POST /orders. Stock is no longer tracked here at
// all -- see Inventory Service's GET /stock for the live, authoritative quantity.
//
// The image endpoints below carry no auth check of their own -- same trust model as every
// other endpoint in this service (and Inventory Service's restock endpoint): the Gateway is
// the one place that enforces the "admin" group requirement (see api-gateway's
// SecurityConfig), this service just trusts that a request reaching it at all has already
// been authorized.
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageUploadService productImageUploadService;

    public ProductController(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ProductImageUploadService productImageUploadService
    ) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productImageUploadService = productImageUploadService;
    }

    @GetMapping
    // @Transactional here (and on getProduct below) now that ProductResponse.from() reads
    // product.getImages() -- that's a LAZY collection, so without an open session at the
    // point it's actually read, Hibernate throws LazyInitializationException instead of
    // silently fetching it. readOnly = true: these never write, which lets Hibernate skip
    // its usual dirty-checking work for the transaction.
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    // Step 1 of the upload flow: hands back a short-lived S3 URL the browser will PUT the
    // actual file to directly (see ProductImageUploadService) -- this call itself never
    // sees any file bytes, it just proves the product exists (and still has room for
    // another image) before minting a URL for it. Checking the cap here, not just at
    // confirmation time, means an admin who's already at the limit finds out immediately
    // rather than after picking a file and waiting for the upload to finish.
    @PostMapping("/{id}/image-upload-url")
    public ResponseEntity<ImageUploadUrlResponse> createImageUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        if (productImageRepository.countByProductId(product.getId()) >= ProductImageUploadService.MAX_IMAGES_PER_PRODUCT) {
            throw new IllegalArgumentException(
                    "This product already has the maximum of %d images".formatted(ProductImageUploadService.MAX_IMAGES_PER_PRODUCT));
        }
        ImageUploadUrlResponse response = productImageUploadService.createUploadUrl(product.getId(), request.getContentType());
        return ResponseEntity.ok(response);
    }

    // Step 2: called only after the browser's direct PUT to S3 has already succeeded --
    // this just adds the uploaded file to the product's image list, it never itself touches
    // file bytes. POST, not PUT: this is now always an addition to a collection, never a
    // wholesale replacement of "the" image the way a single-image column would have been.
    @PostMapping("/{id}/images")
    @Transactional
    public ResponseEntity<ProductResponse> addProductImage(
            @PathVariable Long id,
            @Valid @RequestBody AddProductImageRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        // Re-checked here, not just in createImageUploadUrl: two uploads for the same
        // product started in parallel (two tabs, or two admins) could both pass that
        // earlier check before either one finishes -- this is the check that actually has
        // the last word, right before the row is created.
        if (product.getImages().size() >= ProductImageUploadService.MAX_IMAGES_PER_PRODUCT) {
            // The file itself already landed in S3 by this point -- clean it up rather
            // than leaving an orphan behind just because the count check lost this race.
            productImageUploadService.deleteIfManaged(request.getImageUrl());
            throw new IllegalArgumentException(
                    "This product already has the maximum of %d images".formatted(ProductImageUploadService.MAX_IMAGES_PER_PRODUCT));
        }
        product.getImages().add(new ProductImage(product, request.getImageUrl()));
        // Adding to a cascaded collection only queues the INSERT -- Hibernate doesn't
        // actually run it (and so doesn't assign the new row's sequence-generated id)
        // until the persistence context flushes, which by default wouldn't happen until
        // this transaction commits, after this method has already returned. Without this
        // explicit flush, ProductResponse.from() below would serialize the new image with
        // id: null, since nothing has told Hibernate to run that INSERT yet.
        productImageRepository.flush();
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    // Removes one specific image from a product -- the id path segment (not just the URL)
    // is what makes this addressable per-image now that a product can have several. Looked
    // up by imageId alone and then checked against the product in the path, rather than a
    // derived query, so a mismatched pair (an imageId that's real but belongs to a
    // *different* product) fails clearly as 404 instead of silently deleting the wrong row
    // or the right one for the wrong reason.
    @DeleteMapping("/{id}/images/{imageId}")
    @Transactional
    public ResponseEntity<ProductResponse> deleteProductImage(@PathVariable Long id, @PathVariable Long imageId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));
        if (!image.getProduct().getId().equals(id)) {
            throw new EntityNotFoundException("Image not found: " + imageId);
        }
        // orphanRemoval on Product#images (not a direct repository delete) so this goes
        // through the same cascade Order/OrderItem already relies on -- removing it from
        // the collection is what actually issues the DELETE at flush time.
        product.getImages().remove(image);
        productImageUploadService.deleteIfManaged(image.getImageUrl());
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}
