package com.example.aishopbot.config;

import com.example.aishopbot.client.AiServiceClient;
import com.example.aishopbot.domain.Product;
import com.example.aishopbot.domain.Shop;
import com.example.aishopbot.repository.ProductRepository;
import com.example.aishopbot.repository.ShopRepository;
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
    private final ShopRepository shopRepository;
    private final AiServiceClient aiServiceClient;

    @Override
    public void run(String... args) {
        Shop clothesShop;
        Shop cosmeticsShop;

        if (shopRepository.count() == 0) {
            clothesShop = new Shop();
            clothesShop.setName("Одежда+");
            clothesShop.setDescription("Модная одежда и аксессуары");
            clothesShop = shopRepository.save(clothesShop);

            cosmeticsShop = new Shop();
            cosmeticsShop.setName("Косметика-Мир");
            cosmeticsShop.setDescription("Косметика и уход за собой");
            cosmeticsShop = shopRepository.save(cosmeticsShop);

            log.info("✅ Создано 2 магазина");
        } else {
            List<Shop> shops = shopRepository.findAll();
            clothesShop = shops.get(0);
            cosmeticsShop = shops.size() > 1 ? shops.get(1) : shops.get(0);
        }

        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    createProduct("Футболка Classic", "Хлопок 100%, размеры S-XL, цвет белый/чёрный", "1500", clothesShop.getId()),
                    createProduct("Джинсы Slim", "Деним, размеры 28-36, узкий крой", "3500", clothesShop.getId()),
                    createProduct("Кроссовки Run", "Для бега, амортизация, размеры 36-45", "5000", clothesShop.getId()),
                    createProduct("Худи Oversize", "Тёплое, свободный крой, цвет серый", "2800", clothesShop.getId()),
                    createProduct("Крем для лица", "Увлажняющий, SPF 30, 50мл", "1200", cosmeticsShop.getId()),
                    createProduct("Помада матовая", "Стойкая, оттенок Nude", "800", cosmeticsShop.getId()),
                    createProduct("Шампунь", "Для сухих волос, 400мл", "600", cosmeticsShop.getId())
            ));
            log.info("✅ Загружено {} тестовых товаров", productRepository.count());
        } else {
            log.info("Товары уже есть в БД, пропускаем загрузку");
        }

        aiServiceClient.indexProducts(productRepository.findAll());
        log.info("📦 Индексация товаров в Qdrant запущена");
    }

    private Product createProduct(String name, String description, String price, Long shopId) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setActive(true);
        p.setShopId(shopId);
        return p;
    }
}