package com.example.aishopbot.client;

import com.example.aishopbot.domain.Product;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class AiServiceClient {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String ask(String message, String systemPrompt, Long shopId) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("message", message);
            payload.put("shop_id", shopId);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                payload.put("system_prompt", systemPrompt);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/chat"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("AI-сервис вернул {}: {}", response.statusCode(), response.body());
                return "❌ AI временно недоступен. Попробуйте позже.";
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("reply").asText();

        } catch (Exception e) {
            log.error("Ошибка вызова AI-сервиса: {}", e.getMessage(), e);
            return "❌ Не удалось связаться с AI. Проверьте соединение.";
        }
    }

    public void indexProducts(List<Product> products) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            var arr = payload.putArray("products");
            for (Product p : products) {
                var node = arr.addObject();
                node.put("id", p.getId());
                node.put("shop_id", p.getShopId());
                node.put("name", p.getName());
                node.put("description", p.getDescription());
                node.put("price", p.getPrice().doubleValue());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/index"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("Индексация товаров: {} — {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Ошибка индексации товаров: {}", e.getMessage(), e);
        }
    }
}