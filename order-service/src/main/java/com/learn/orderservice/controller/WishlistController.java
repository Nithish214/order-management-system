package com.learn.orderservice.controller;

import com.learn.orderservice.dto.WishlistItemResponse;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.entity.WishlistItem;
import com.learn.orderservice.repository.AppUserRepository;
import com.learn.orderservice.repository.ProductRepository;
import com.learn.orderservice.repository.WishlistItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Save-for-later, one list per signed-in user -- deliberately the same shape as
// CartController: a product reference read live at request time (see WishlistItem's own
// comment), findOrCreateUser for the same first-request-ever race, "any authenticated
// user" at the Gateway (/users/me/** -- see api-gateway's SecurityConfig) since this is
// always the caller's OWN wishlist, identity from the trusted header, never the URL.
@RestController
@RequestMapping("/users/me/wishlist")
public class WishlistController {

    private final WishlistItemRepository wishlistItemRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;

    public WishlistController(
            WishlistItemRepository wishlistItemRepository,
            AppUserRepository appUserRepository,
            ProductRepository productRepository
    ) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
    }

    // Not readOnly -- same reason as CartController#getCart: findOrCreateUser can insert a
    // brand-new AppUser row, and Postgres refuses to advance a sequence inside a
    // read-only transaction.
    @GetMapping
    @Transactional
    public ResponseEntity<List<WishlistItemResponse>> getWishlist(
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        return ResponseEntity.ok(currentWishlist(user.getId(), isAdmin));
    }

    // Saving an already-saved product is a no-op, not an error and not a second row --
    // there's nothing meaningfully different about "save" and "already saved," so this
    // stays idempotent rather than making the frontend check first.
    @PostMapping("/{productId}")
    @Transactional
    public ResponseEntity<List<WishlistItemResponse>> addItem(
            @PathVariable Long productId,
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        if (!wishlistItemRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
            wishlistItemRepository.save(new WishlistItem(user, product));
        }
        return ResponseEntity.ok(currentWishlist(user.getId(), isAdmin));
    }

    // Removing something not on the list is also a no-op for the same reason -- the
    // caller's intent ("this product should not be on my wishlist") is already satisfied.
    @DeleteMapping("/{productId}")
    @Transactional
    public ResponseEntity<List<WishlistItemResponse>> removeItem(
            @PathVariable Long productId,
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        wishlistItemRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return ResponseEntity.ok(currentWishlist(user.getId(), isAdmin));
    }

    private List<WishlistItemResponse> currentWishlist(Long userId, boolean isAdmin) {
        return wishlistItemRepository.findAllByUserIdWithProduct(userId)
                .stream()
                .map(item -> WishlistItemResponse.from(item, isAdmin))
                .toList();
    }

    // Same find-or-create as CartController's own copy -- covers the same first-request
    // race (see AppUserRepository#findOrCreate's own comment).
    private AppUser findOrCreateUser(String cognitoSub) {
        return appUserRepository.findOrCreate(cognitoSub);
    }
}
