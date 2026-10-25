package com.example.aishopbot.domain;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AiChatResponse {

    private String reply;

    private List<ProductShort> products;

    @Data
    public static class ProductShort {
        private Long id;
        private String name;
        private String category;
        private String description;
        private Double price;

        @JsonProperty("image_url")
        private String imageUrl;
    }
}