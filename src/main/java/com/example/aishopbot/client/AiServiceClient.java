package com.example.aishopbot.client;

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
import java.time.Duration;

@Slf4j
@Component
public class AiServiceClient {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String ask(String message, String systemPrompt) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("message", message);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                payload.put("system_prompt", systemPrompt);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/chat"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

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
}