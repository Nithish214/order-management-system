package com.learn.orderservice.repository;

import com.learn.orderservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Native SQL, not a derived query method or JPQL -- @@ (the tsvector "matches" operator)
    // and ts_rank() are Postgres-specific full-text search functions with no JPQL
    // equivalent.
    //
    // to_tsquery, not plainto_tsquery -- the difference matters. plainto_tsquery only ever
    // does WHOLE-WORD matching after stemming (reducing a word to its linguistic root, e.g.
    // "mouse" is stored as the stem "mous", the same as "mousing"/"mice" would reduce to)
    // -- found live, the hard way, that this meant searching "web" never matched "Webcam":
    // stemming only relates words that are grammatical variants of EACH OTHER, and "web" is
    // simply a different word from "webcam", not a shorter inflection of it. :query here is
    // expected to already be a hand-built tsquery string with a `:*` prefix marker appended
    // to each search term (see ProductController.toPrefixTsQuery) -- `:*` is what makes
    // to_tsquery match "web:*" against any indexed word STARTING WITH "web", "webcam"
    // included, while "mous:*" still matches "mouse" too (the stemmed lexeme actually
    // stored for it is "mous", so the prefix and the stem happen to coincide there).
    //
    // ts_rank scores each match by relevance (how many times the term appears, how rare it
    // is across the whole indexed text) -- ORDER BY that descending is what makes a product
    // whose NAME is "Mechanical Keyboard" outrank one that merely mentions "keyboard" once
    // in passing, the actual point of using a real ranking function instead of just
    // filtering with no particular order.
    @Query(value = "SELECT * FROM product WHERE search_vector @@ to_tsquery('english', :query) "
            + "ORDER BY ts_rank(search_vector, to_tsquery('english', :query)) DESC",
            nativeQuery = true)
    List<Product> searchByPrefixTsQuery(@Param("query") String query);
}
