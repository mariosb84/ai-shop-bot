package com.example.aishopbot.domain;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_states")
public class UserState {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    private String state;

    @Column(name = "selected_shop_id")
    private Long selectedShopId;

    @Column(name = "catalog_page")
    private Integer catalogPage = 0;

    @Column(name = "pending_product_id")
    private Long pendingProductId;

    @Column(name = "pending_name", length = 100)
    private String pendingName;

    @Column(name = "pending_phone", length = 20)
    private String pendingPhone;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}