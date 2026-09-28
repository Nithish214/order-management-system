package com.learn.orderservice.repository;

import com.learn.orderservice.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    // join fetch product, not left join -- product_id is NOT NULL (see the migration), so
    // an inner join can never silently drop a row. Newest-saved-first, same reasoning as
    // the migration's own index comment.
    @Query("select wi from WishlistItem wi join fetch wi.product where wi.user.id = :userId order by wi.createdAt desc, wi.id desc")
    List<WishlistItem> findAllByUserIdWithProduct(@Param("userId") Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
