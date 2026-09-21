package com.learn.orderservice.controller;

import com.learn.orderservice.dto.AddCartItemRequest;
import com.learn.orderservice.dto.CartItemResponse;
import com.learn.orderservice.dto.SetCartItemQuantityRequest;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.entity.CartItem;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.repository.AppUserRepository;
import com.learn.orderservice.repository.CartItemRepository;
import com.learn.orderservice.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// One cart per signed-in user, stored server-side -- replaces what used to live only in
// the browser's localStorage, specifically so a cart follows an account across
// devices/browsers rather than staying stuck in the one browser it was built up in.
// Every endpoint here just needs "any authenticated user" at the Gateway (see
// api-gateway's SecurityConfig's default rule) -- there's nothing admin-only about
// managing your own cart.
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;

    public CartController(
            CartItemRepository cartItemRepository,
            AppUserRepository appUserRepository,
            ProductRepository productRepository
    ) {
        this.cartItemRepository = cartItemRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
    }

    // Not readOnly -- findOrCreateUser below can insert a brand-new AppUser row (the
    // narrow race where this is the very first request Order Service has ever seen for
    // this Cognito identity), and Postgres correctly refuses to advance a sequence
    // (nextval) inside a read-only transaction. Found by actually hitting this endpoint
    // as a genuinely new user, not by inspection -- readOnly looked right at a glance
    // since GET requests are the textbook case for it.
    @GetMapping
    @Transactional
    public ResponseEntity<List<CartItemResponse>> getCart(
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        return ResponseEntity.ok(currentCart(user.getId(), isAdmin));
    }

    // Adding a product already in the cart increments its existing row's quantity
    // (per the unique (user_id, product_id) constraint -- see the migration) rather than
    // creating a second row for the same product, same "merge, don't duplicate" behavior
    // the old client-only cart's addItem already had.
    @PostMapping("/items")
    @Transactional
    public ResponseEntity<List<CartItemResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + request.getProductId()));

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElseGet(() -> new CartItem(user, product, 0));
        item.setQuantity(item.getQuantity() + request.getQuantity());
        cartItemRepository.save(item);

        return ResponseEntity.ok(currentCart(user.getId(), isAdmin));
    }

    // Sets an exact quantity rather than adding to it -- zero or negative removes the
    // item entirely (same convention the old client-only cart used), rather than leaving
    // a zero-quantity row behind.
    @PutMapping("/items/{productId}")
    @Transactional
    public ResponseEntity<List<CartItemResponse>> setItemQuantity(
            @PathVariable Long productId,
            @Valid @RequestBody SetCartItemQuantityRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        if (request.getQuantity() <= 0) {
            cartItemRepository.deleteByUserIdAndProductId(user.getId(), productId);
        } else {
            CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                    .orElseThrow(() -> new EntityNotFoundException("Cart has no item for product " + productId));
            item.setQuantity(request.getQuantity());
        }
        return ResponseEntity.ok(currentCart(user.getId(), isAdmin));
    }

    @DeleteMapping("/items/{productId}")
    @Transactional
    public ResponseEntity<List<CartItemResponse>> removeItem(
            @PathVariable Long productId,
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        AppUser user = findOrCreateUser(cognitoSub);
        cartItemRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return ResponseEntity.ok(currentCart(user.getId(), isAdmin));
    }

    // Called right after a successful order placement (see CartSummary's
    // handlePlaceOrder) to empty the cart that was just checked out -- the order itself
    // already has its own, separate, permanent copy of what was purchased (OrderItem),
    // so nothing is lost by clearing this working-set table.
    @DeleteMapping
    @Transactional
    public ResponseEntity<List<CartItemResponse>> clearCart(@RequestHeader("X-User-Sub") String cognitoSub) {
        AppUser user = findOrCreateUser(cognitoSub);
        cartItemRepository.deleteAllByUserId(user.getId());
        return ResponseEntity.ok(List.of());
    }

    private List<CartItemResponse> currentCart(Long userId, boolean isAdmin) {
        return cartItemRepository.findAllByUserIdWithProduct(userId)
                .stream()
                .map(item -> CartItemResponse.from(item, isAdmin))
                .toList();
    }

    // Same find-or-create as OrderCreationService#createUserForCognitoSub -- covers the
    // narrow race where a cart action reaches this service before the frontend's
    // fire-and-forget POST /users/me (see AuthContext's login()) has finished syncing the
    // real profile. Placeholder email/name get overwritten by that sync whenever it lands.
    private AppUser findOrCreateUser(String cognitoSub) {
        return appUserRepository.findByCognitoSub(cognitoSub).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setCognitoSub(cognitoSub);
            user.setEmail(cognitoSub + "@cognito.local");
            user.setName("Cognito User");
            return appUserRepository.save(user);
        });
    }
}
