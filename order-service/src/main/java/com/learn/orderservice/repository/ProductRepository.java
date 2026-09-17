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
    // does WHOLE-WORD matching after stemming, which caused two separate real bugs before
    // landing on this version:
    //   1. "web" never matched "Webcam" -- stemming only relates words that are
    //      grammatical variants of EACH OTHER ("mouse"/"mousing"), and "web" is simply a
    //      different word from "webcam", not a shorter inflection of it. Fixed by
    //      appending a `:*` prefix marker to each search term (see
    //      ProductController.toPrefixTsQuery) so to_tsquery does PREFIX matching instead.
    //   2. "we" still never matched "Webcam" even with prefix matching -- the 'english'
    //      configuration treats "we" as a STOPWORD (same category as "the", "a", "is")
    //      and strips it from the query entirely regardless of any `:*` suffix. Fixed by
    //      switching search_vector (V12__switch_product_search_to_simple_config.sql) and
    //      this query to the 'simple' configuration -- no stemming, no stopword list,
    //      just lowercasing and tokenizing. The right fit for short product names, not
    //      natural-language sentences: "mous" still matches "mouse" fine (a literal
    //      character prefix, stemming was never actually required for that case), and
    //      now short/common words like "we" work too.
    //
    // ts_rank scores each match by relevance (how many times the term appears, how rare it
    // is across the whole indexed text) -- ORDER BY that descending is what makes a product
    // whose NAME is "Mechanical Keyboard" outrank one that merely mentions "keyboard" once
    // in passing, the actual point of using a real ranking function instead of just
    // filtering with no particular order.
    @Query(value = "SELECT * FROM product WHERE search_vector @@ to_tsquery('simple', :query) "
            + "ORDER BY ts_rank(search_vector, to_tsquery('simple', :query)) DESC",
            nativeQuery = true)
    List<Product> searchByPrefixTsQuery(@Param("query") String query);
}
