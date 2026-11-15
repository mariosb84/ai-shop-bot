package com.example.aishopbot.domain;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    /* Телефон оператора для связи с клиентом */
    @Column(name = "operator_phone", length = 20)
    private String operatorPhone;

    /* ChatId админа магазина (кому слать уведомления о заказах) */
    @Column(name = "admin_chat_id")
    private Long adminChatId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}