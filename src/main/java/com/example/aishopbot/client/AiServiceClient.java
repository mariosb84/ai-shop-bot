package com.example.aishopbot.client;

import com.example.aishopbot.domain.AiChatResponse;
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

    public AiChatResponse ask(String message, String systemPrompt, Long shopId) {
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
                AiChatResponse fallback = new AiChatResponse();
                fallback.setReply("❌ AI временно недоступен. Попробуйте позже.");
                fallback.setProducts(List.of());
                return fallback;
            }

            return objectMapper.readValue(response.body(), AiChatResponse.class);

        } catch (Exception e) {
            log.error("Ошибка вызова AI-сервиса: {}", e.getMessage(), e);
            AiChatResponse fallback = new AiChatResponse();
            fallback.setReply("❌ Не удалось связаться с AI. Проверьте соединение.");
            fallback.setProducts(List.of());
            return fallback;
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
                node.put("category", p.getCategory());
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

    public JsonNode uploadCatalog(Long shopId, byte[] fileBytes, String filename) {
        try {
            String boundary = "----Boundary" + System.currentTimeMillis();
            var body = new java.io.ByteArrayOutputStream();

            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"shop_id\"\r\n\r\n" + shopId + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/load-catalog"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("Загрузка каталога: {} — {}", response.statusCode(), response.body());
            if (response.statusCode() != 200) return null;
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Ошибка загрузки каталога: {}", e.getMessage(), e);
            return null;
        }
    }

}
