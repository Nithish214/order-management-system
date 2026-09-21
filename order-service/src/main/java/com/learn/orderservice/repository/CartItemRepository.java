package com.learn.orderservice.repository;

import com.learn.orderservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // join fetch product, not left join -- product_id is NOT NULL (see the migration), so
    // an inner join can never silently drop a row the way it legitimately can for
    // Order/OrderItem's optional associations.
    @Query("select ci from CartItem ci join fetch ci.product where ci.user.id = :userId order by ci.id asc")
    List<CartItem> findAllByUserIdWithProduct(@Param("userId") Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    void deleteAllByUserId(Long userId);
}
