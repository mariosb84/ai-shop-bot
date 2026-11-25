package com.example.aishopbot.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_price", precision = 10, scale = 2)
    private BigDecimal productPrice;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_comment", columnDefinition = "TEXT")
    private String customerComment;

    @Column(name = "customer_chat_id")
    private Long customerChatId;

    @Column(name = "customer_username", length = 100)
    private String customerUsername;

    @Column(length = 20)
    private String status = "new";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}