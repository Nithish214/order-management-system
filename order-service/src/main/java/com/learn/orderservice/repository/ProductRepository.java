package com.learn.orderservice.repository;

import com.learn.orderservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Native SQL, not a derived query method or JPQL -- @@ (the tsvector "matches" operator)
    // and ts_rank() are Postgres-specific full-text search functions with no JPQL
    // equivalent. plainto_tsquery parses the raw search string into the same kind of
    // tsquery the @@ operator expects, handling multiple words (implicitly AND'd together)
    // and normalizing case/word-endings the same way search_vector's own to_tsvector did
    // when the column was generated (see V11__add_product_search.sql) -- searching
    // "keyboards" still matches a product indexed under "keyboard" for exactly that reason.
    //
    // ts_rank scores each match by relevance (how many times the term appears, how rare it
    // is across the whole indexed text) -- ORDER BY that descending is what makes a product
    // whose NAME is "Mechanical Keyboard" outrank one that merely mentions "keyboard" once
    // in passing, the actual point of using a real ranking function instead of just
    // filtering with no particular order.
    @Query(value = "SELECT * FROM product WHERE search_vector @@ plainto_tsquery('english', :query) "
            + "ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC",
            nativeQuery = true)
    List<Product> searchByKeyword(@Param("query") String query);
}
