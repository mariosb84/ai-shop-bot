package com.example.aishopbot.repository;


import com.example.aishopbot.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByShopIdOrderByCreatedAtDesc(Long shopId);
    List<Order> findAllByOrderByCreatedAtDesc();
}