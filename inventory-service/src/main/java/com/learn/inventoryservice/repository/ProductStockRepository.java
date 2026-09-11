package com.learn.inventoryservice.repository;

import com.learn.inventoryservice.entity.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
}
