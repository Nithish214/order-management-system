package com.learn.orderservice.controller;

import com.learn.orderservice.dto.AddProductImageRequest;
import com.learn.orderservice.dto.CategoryResponse;
import com.learn.orderservice.dto.ImageUploadUrlRequest;
import com.learn.orderservice.dto.ImageUploadUrlResponse;
import com.learn.orderservice.dto.PagedResponse;
import com.learn.orderservice.dto.ProductResponse;
import com.learn.orderservice.dto.SetProductVideoRequest;
import com.learn.orderservice.dto.VideoUploadUrlRequest;
import com.learn.orderservice.dto.VideoUploadUrlResponse;
import com.learn.orderservice.entity.Product;
import com.learn.orderservice.entity.ProductImage;
import com.learn.orderservice.repository.ProductImageRepository;
import com.learn.orderservice.repository.ProductRepository;
import com.learn.orderservice.repository.ReviewRepository;
import com.learn.orderservice.service.ProductImageUploadService;
import com.learn.orderservice.service.ProductVideoUploadService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    // 100 balances two things: small enough that a page's worth of images (see
    // Product.images' @BatchSize(size = 100)) fits in one extra batched query rather
    // than several, and large enough that browsing 300+ products doesn't feel like
    // constant clicking. MAX_PAGE_SIZE guards against a client (accidentally or not)
    // requesting an enormous page and defeating the whole point of paginating -- these
    // endpoints have no auth of their own (see this class's own comment on that), so
    // nothing else stops a caller from asking for size=1000000 otherwise.
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageUploadService productImageUploadService;
    private final ProductVideoUploadService productVideoUploadService;
    private final ReviewRepository reviewRepository;

    public ProductController(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ProductImageUploadService productImageUploadService,
            ProductVideoUploadService productVideoUploadService,
            ReviewRepository reviewRepository
    ) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productImageUploadService = productImageUploadService;
        this.productVideoUploadService = productVideoUploadService;
        this.reviewRepository = reviewRepository;
    }

    // Optional ?category= filters to one category; omitted (or blank) returns everything,
    // same as before this parameter existed -- an existing caller with no idea this filter
    // now exists keeps working exactly as it always did.
    //
    // Paginated now (page/size, both optional, 0-indexed page to match Spring Data's own
    // Pageable) -- was deliberately NOT paginated back when this returned ~50-ish rows (a
    // few tens of KB, trivial for a browser either way), but the catalog since grew past
    // the "an order of magnitude larger" mark that comment named as the point worth
    // revisiting this at. See PagedResponse for the response shape this returns now
    // instead of a plain array.
    //
    // Optional ?sort= picks how results are ordered -- "featured" (the default) is the
    // same stable id-ascending order pagination already needed for correctness (see
    // buildPageable/resolveSort below); everything else is the common e-commerce set
    // given the data this catalog actually has (price, id as a proxy for "when it was
    // added", name) -- no ratings/sales data exists to support something like "Best
    // Sellers".
    @GetMapping
    // @Transactional here (and on getProduct below) now that ProductResponse.from() reads
    // product.getImages() -- that's a LAZY collection, so without an open session at the
    // point it's actually read, Hibernate throws LazyInitializationException instead of
    // silently fetching it. readOnly = true: these never write, which lets Hibernate skip
    // its usual dirty-checking work for the transaction.
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "featured") String sort,
            // Set by the Gateway's UserIdentityHeaderFilter, never by the caller directly
            // (these ports are off the public internet -- see this class's own comment).
            // defaultValue "false" also covers local/Postman testing straight against
            // this service, where no gateway ever sets it at all.
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Product> results = (category == null || category.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.findByCategory(category, pageable);
        return ResponseEntity.ok(PagedResponse.from(results.map(product -> ProductResponse.from(product, isAdmin))));
    }

    // Shared by every paginated endpoint below -- keeps size within (1, MAX_PAGE_SIZE]
    // rather than trusting whatever a caller passes straight through to PageRequest.of,
    // which would throw its own (less clear) exception for a negative/zero size anyway.
    private static int clampPageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    // Whitelist mapping from the ?sort= string to an actual Sort -- never builds one
    // from raw client input directly (that would let a caller sort/inject on arbitrary
    // column names), and falls back to the same "featured" order for anything it doesn't
    // recognize rather than erroring, so an old bookmarked URL or a typo just gets the
    // default instead of a 400.
    //
    // id ascending IS "featured" here: this catalog has no actual merchandising/curation
    // concept (no "featured" flag on Product), so insertion order is what stands in for
    // it -- same default LIMIT/OFFSET pagination already needed anyway for the reason
    // explained below.
    private static Sort resolveSort(String sort) {
        return switch (sort) {
            case "price_asc" -> Sort.by("unitPrice").ascending();
            case "price_desc" -> Sort.by("unitPrice").descending();
            case "newest" -> Sort.by("id").descending();
            case "name_asc" -> Sort.by("name").ascending();
            default -> Sort.by("id").ascending();
        };
    }

    // A LIMIT/OFFSET query with no ORDER BY has no guaranteed row order at all -- Postgres
    // is free to return rows in whatever order it finds convenient (physical scan order
    // today, but nothing stops that changing after an UPDATE, a VACUUM, or on a replica).
    // Without SOME explicit order, two different page requests aren't guaranteed to agree
    // on what's "row 101" versus "row 200" -- a product could silently appear on two
    // pages, or on neither, purely because the underlying scan order shifted between
    // requests. Whichever Sort resolveSort() picks makes every page's boundary
    // well-defined and stable, not just the id-ascending default.
    //
    // NEVER pass a Pageable built by this to searchByPrefixTsQuery -- see that method's
    // call site for why a Sorted Pageable there is an outright 500, not just a no-op.
    // Safe everywhere else (findAll/findByCategory), which is exactly where a caller's
    // ?sort= actually needs to take effect. That's also why the frontend hides the sort
    // dropdown while an actual search is active -- there's genuinely nowhere for it to
    // safely apply once a keyword match is in play.
    private static Pageable buildPageable(int page, int size, String sort) {
        return PageRequest.of(page, clampPageSize(size), resolveSort(sort));
    }

    // Backs the sidebar/nav -- one row per category, with how many products are actually
    // in it, so the frontend can render "Electronics (13)" without a separate request (or
    // client-side counting) per category. A literal path segment, same reasoning as
    // /search above it in this file -- always wins over /{id} for the same HTTP method
    // regardless of declaration order.
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(productRepository.findCategorySummaries());
    }

    // A literal path segment ("/products/search") always wins over a path-variable one
    // ("/products/{id}") for the same HTTP method, regardless of which is declared first --
    // standard Spring MVC route matching, not something that depends on this method's
    // position in the file. Backed by Postgres full-text search (see
    // V11__add_product_search.sql and ProductRepository.searchByPrefixTsQuery) -- used to
    // be Postgres/RDS only, since local dev ran against Oracle and never got the
    // equivalent migration; now that local dev runs Postgres too, this works everywhere.
    //
    // Blank/missing q returns every product, same shape as plain GET /products -- lets the
    // frontend use one endpoint for "no search yet" and "actively searching" rather than
    // switching between two different calls as the user types and clears the search box.
    // Paginated the same way GET /products now is -- see that method's comment. sort is
    // only accepted here for the blank-q fallback (which is just findAll under the hood,
    // same as plain GET /products) -- an actual keyword match always stays ranked by
    // relevance regardless of what sort asks for.
    //
    // Found the hard way (a live 500, not just reasoning about it) that a Sorted Pageable
    // can NOT be passed to searchByPrefixTsQuery: Spring Data doesn't skip sorting a
    // native @Query just because the SQL already has its own ORDER BY -- it appends the
    // Pageable's Sort as a SECOND "order by" clause regardless, which Postgres rejects
    // outright ("syntax error at or near 'order'"). So the ranked branch below
    // deliberately builds its OWN plain, unsorted Pageable rather than reusing `pageable`
    // -- the native query's baked-in ORDER BY ts_rank DESC is the only sort that's safe
    // to combine with it.
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<ProductResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "featured") String sort,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        String prefixQuery = toPrefixTsQuery(q);
        Page<Product> results = prefixQuery.isEmpty()
                ? productRepository.findAll(buildPageable(page, size, sort))
                : productRepository.searchByPrefixTsQuery(prefixQuery, PageRequest.of(page, clampPageSize(size)));
        return ResponseEntity.ok(PagedResponse.from(results.map(product -> ProductResponse.from(product, isAdmin))));
    }

    // Turns raw user input ("web cam") into a Postgres tsquery string ("web:* & cam:*")
    // that does PREFIX matching on every word, not just whole-word matching after stemming
    // -- found live that searching "web" never matched "Webcam" without this, since
    // stemming (what plainto_tsquery/a plain to_tsquery without `:*` would do) only relates
    // words that are grammatical variants of each other, and "web" isn't a shorter
    // inflection of "webcam", just a different word that happens to share a prefix.
    //
    // Each token is stripped down to letters/digits before the `:*` suffix is appended --
    // not an XSS/SQL-injection concern (this string is still bound as a query PARAMETER,
    // never concatenated into SQL), but tsquery has its OWN small operator language
    // (`&`, `|`, `!`, `(`, `)`, `:`) that a raw search term containing those characters
    // would otherwise be parsed as, throwing a Postgres syntax error instead of just
    // treating them as plain text to search for.
    private static String toPrefixTsQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        return Arrays.stream(rawQuery.trim().split("\\s+"))
                .map(token -> token.replaceAll("[^a-zA-Z0-9]", ""))
                .filter(token -> !token.isEmpty())
                .map(token -> token + ":*")
                .collect(Collectors.joining(" & "));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        ProductResponse response = ProductResponse.from(product, isAdmin);
        // Only here, not on the list/search endpoints below -- see ProductResponse's own
        // comment on averageRating for why a single extra query per product is fine for
        // one product but not for a whole page of them.
        ReviewRepository.RatingSummary ratingSummary = reviewRepository.getRatingSummary(id);
        response.setAverageRating(ratingSummary.getAverageRating());
        response.setReviewCount(ratingSummary.getReviewCount() != null ? ratingSummary.getReviewCount().intValue() : 0);
        return ResponseEntity.ok(response);
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

    // Step 1 of the video upload flow -- same two-step pattern as image-upload-url, no
    // count-cap check needed since a product has at most one video (see Product#videoUrl).
    @PostMapping("/{id}/video-upload-url")
    public ResponseEntity<VideoUploadUrlResponse> createVideoUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody VideoUploadUrlRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        VideoUploadUrlResponse response = productVideoUploadService.createUploadUrl(product.getId(), request.getContentType());
        return ResponseEntity.ok(response);
    }

    // Step 2: called only after the browser's direct PUT to S3 has already succeeded.
    // PUT, not POST -- unlike images, this always replaces the single existing value
    // (or sets it for the first time), never adds to a collection.
    @PutMapping("/{id}/video")
    @Transactional
    public ResponseEntity<ProductResponse> setProductVideo(
            @PathVariable Long id,
            @Valid @RequestBody SetProductVideoRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        // Clean up whatever this is replacing (a no-op if there was none yet) before
        // overwriting the field -- otherwise the old file would linger in S3 forever with
        // nothing left pointing at it.
        productVideoUploadService.deleteIfManaged(product.getVideoUrl());
        product.setVideoUrl(request.getVideoUrl());
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    // Removes this product's video entirely, if it has one.
    @DeleteMapping("/{id}/video")
    @Transactional
    public ResponseEntity<ProductResponse> deleteProductVideo(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        productVideoUploadService.deleteIfManaged(product.getVideoUrl());
        product.setVideoUrl(null);
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}
