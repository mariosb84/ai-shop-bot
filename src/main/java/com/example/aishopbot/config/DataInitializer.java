package com.example.aishopbot.config;

import com.example.aishopbot.client.AiServiceClient;
import com.example.aishopbot.domain.Product;
import com.example.aishopbot.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final AiServiceClient aiServiceClient;

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    createProduct("Футболка Classic", "Хлопок 100%, размеры S-XL, цвет белый/чёрный", "1500"),
                    createProduct("Джинсы Slim", "Деним, размеры 28-36, узкий крой", "3500"),
                    createProduct("Кроссовки Run", "Для бега, амортизация, размеры 36-45", "5000"),
                    createProduct("Худи Oversize", "Тёплое, свободный крой, цвет серый", "2800")
            ));
            log.info("✅ Загружено {} тестовых товаров", productRepository.count());
        } else {
            log.info("Товары уже есть в БД, пропускаем загрузку");
        }

        /* Всегда индексируем товары в Qdrant*/
        aiServiceClient.indexProducts(productRepository.findAll());
        log.info("📦 Индексация товаров в Qdrant запущена");
    }

    private Product createProduct(String name, String description, String price) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setActive(true);
        return p;
    }

}