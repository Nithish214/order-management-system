package com.learn.orderservice.controller;

import com.learn.orderservice.dto.CreateReviewRequest;
import com.learn.orderservice.dto.ReviewEligibilityResponse;
import com.learn.orderservice.dto.ReviewResponse;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.entity.Review;
import com.learn.orderservice.repository.AppUserRepository;
import com.learn.orderservice.repository.OrderItemRepository;
import com.learn.orderservice.repository.ProductRepository;
import com.learn.orderservice.repository.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every endpoint here just needs "any authenticated user" at the Gateway (see
// api-gateway's SecurityConfig's default rule) -- same trust model as CartController.
// Reviewing is deliberately gated on having actually bought the product (see
// checkEligibility/create below), not just being logged in -- the whole point of a review
// is that it comes from someone who really used the thing.
@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;
    private final OrderItemRepository orderItemRepository;

    public ReviewController(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            AppUserRepository appUserRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewResponse>> getReviews(@PathVariable Long productId) {
        List<ReviewResponse> reviews = reviewRepository.findByProduct_IdOrderByCreatedAtDesc(productId)
                .stream()
                .map(ReviewResponse::from)
                .toList();
        return ResponseEntity.ok(reviews);
    }

    // Deliberately findByCognitoSub, not the findOrCreateUser pattern CartController/
    // OrderCreationService use -- creating a placeholder AppUser row just to immediately
    // tell them "no, you haven't bought anything" would be pointless churn. No AppUser row
    // at all means no CONFIRMED order could possibly exist for them either way, so
    // "not eligible" is already the correct answer without creating anything.
    @GetMapping("/eligibility")
    @Transactional(readOnly = true)
    public ResponseEntity<ReviewEligibilityResponse> checkEligibility(
            @PathVariable Long productId,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        return appUserRepository.findByCognitoSub(cognitoSub)
                .map(user -> {
                    boolean purchased = orderItemRepository.existsByProduct_IdAndOrder_UserIdAndOrder_Status(
                            productId, user.getId(), OrderStatus.CONFIRMED);
                    boolean alreadyReviewed = reviewRepository.existsByProduct_IdAndUser_Id(productId, user.getId());
                    return ResponseEntity.ok(new ReviewEligibilityResponse(purchased, alreadyReviewed));
                })
                .orElseGet(() -> ResponseEntity.ok(new ReviewEligibilityResponse(false, false)));
    }

    // Deliberately NOT @Transactional at this level -- reviewRepository.save() below
    // already gets its own transaction from Spring Data by default, which is exactly what
    // lets the catch block below actually work. Postgres aborts an entire transaction the
    // instant one statement in it violates a constraint; if this whole method (the insert
    // AND the catch-and-recover logic) shared one transaction, this method's own commit at
    // the very end would still fail against that already-aborted transaction, turning a
    // clean 409 into an unhandled 500 -- found by actually triggering the duplicate-review
    // case locally, not by inspection. Letting save() own its own transaction means that
    // transaction is already fully rolled back by the time the exception reaches this
    // catch block, so returning a plain response here needs nothing further from the DB.
    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        // 404, not 403 -- from this endpoint's point of view, someone with no AppUser row
        // at all has exactly the same "never bought this" status as someone who has an
        // account but genuinely never ordered it. No need for the eligibility check's
        // separate not-found branch to exist here too.
        AppUser user = appUserRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new EntityNotFoundException("No purchase history found for this account"));

        // Re-checked here even though the frontend calls GET .../eligibility first --
        // that check only decides whether to *show* the form (see
        // ReviewEligibilityResponse's own comment); this is the enforcement that actually
        // matters, the same "shown vs. actually allowed" split as every admin-only control
        // in this app.
        boolean purchased = orderItemRepository.existsByProduct_IdAndOrder_UserIdAndOrder_Status(
                productId, user.getId(), OrderStatus.CONFIRMED);
        if (!purchased) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Review review = new Review(product, user, request.rating(), request.comment());
            Review saved = reviewRepository.save(review);
            return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(saved));
        } catch (DataIntegrityViolationException ex) {
            // uq_review_product_user -- the existsBy check above already covers the common
            // case, this only fires on a genuine race between two near-simultaneous
            // requests from the same buyer for the same product.
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
