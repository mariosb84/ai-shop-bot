package com.example.aishopbot.repository;

import com.example.aishopbot.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByActiveTrue();
    List<Product> findAllByShopIdAndActiveTrue(Long shopId);
}