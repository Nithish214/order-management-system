package com.learn.orderservice.repository;

import com.learn.orderservice.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    long countByProductId(Long productId);
}
