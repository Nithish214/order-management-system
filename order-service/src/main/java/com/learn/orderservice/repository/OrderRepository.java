package com.learn.orderservice.repository;

import com.learn.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select distinct o from Order o join fetch o.user u left join fetch o.items i left join fetch i.product where o.id = :id")
    Optional<Order> findByIdWithUserAndItems(@Param("id") Long id);

    @Query("select distinct o from Order o join fetch o.user u left join fetch o.items i left join fetch i.product where o.user.id = :userId order by o.createdAt desc")
    List<Order> findAllByUserIdWithUserAndItems(@Param("userId") Long userId);
}
