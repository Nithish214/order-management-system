package com.learn.orderservice.dto;

// One row of GET /products/categories -- backs the sidebar/nav (see
// ProductRepository.findCategorySummaries), which needs both the category name to filter
// by and a count to actually display next to it ("Electronics (13)"), not just the name
// alone.
public record CategoryResponse(String category, long count) {
}
