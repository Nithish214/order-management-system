package com.learn.orderservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// Wraps whatever GET /products, /products?category=, and /products/search now return --
// a plain List<ProductResponse> stopped being enough once the catalog grew past a
// couple hundred rows (see ProductController's own comment on why pagination exists
// now): the frontend needs to know not just "here are 100 products" but "there are 5
// pages total, this is page 0" to render Previous/Next controls at all. page/size echo
// back exactly what was requested (0-indexed, matching Spring Data's own Pageable), so
// the frontend never has to separately track what it asked for.
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
