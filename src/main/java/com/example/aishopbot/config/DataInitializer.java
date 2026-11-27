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

    @org.springframework.beans.factory.annotation.Value("${app.seed-test-data:true}")
    private boolean seedTestData;

    @Override
    public void run(String... args) {
        /* Создаём магазины, если их нет */
        Shop parfumShop;
        Shop clothesShop;

        if (shopRepository.count() == 0) {
            /* Парфюмерия — основной магазин для реального кейса */
            parfumShop = new Shop();
            parfumShop.setName("Парфюмерия");
            parfumShop.setDescription("Нишевая и селективная парфюмерия");
            parfumShop.setActive(true);
            parfumShop = shopRepository.save(parfumShop);

            /* Одежда — скрываем, оставляем для тестов */
            clothesShop = new Shop();
            clothesShop.setName("Одежда+");
            clothesShop.setDescription("Модная одежда и аксессуары (скрыт)");
            clothesShop.setActive(false);
            clothesShop = shopRepository.save(clothesShop);

            log.info("✅ Создано 2 магазина: Парфюмерия (активна), Одежда+ (скрыт)");
        } else {
            List<Shop> shops = shopRepository.findAll();
            parfumShop = shops.stream()
                    .filter(s -> "Парфюмерия".equals(s.getName()))
                    .findFirst()
                    .orElse(shops.get(0));
            clothesShop = shops.stream()
                    .filter(s -> "Одежда+".equals(s.getName()))
                    .findFirst()
                    .orElse(null);
        }

        /* Загружаем тестовые товары, если их нет */
        if (seedTestData && productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    createProduct("Bleu de Chanel EDP", "Древесно-фужерный, ноты: грейпфрут, ладан, сандал", "12500", parfumShop.getId(),
                            "https://picsum.photos/seed/bleu/600/400"),
                    createProduct("Dior Sauvage EDT", "Свежий фужерный, ноты: бергамот, перец, амброксан", "9800", parfumShop.getId(),
                            "https://picsum.photos/seed/sauvage/600/400"),
                    createProduct("Creed Aventus", "Фруктово-древесный, ноты: ананас, берёза, мускус", "45000", parfumShop.getId(),
                            "https://picsum.photos/seed/aventus/600/400"),
                    createProduct("Baccarat Rouge 540", "Амброво-древесный, ноты: шафран, жасмин, амбра", "32000", parfumShop.getId(),
                            "https://picsum.photos/seed/baccarat/600/400"),
                    createProduct("Tom Ford Oud Wood", "Древесно-восточный, ноты: уд, сандал, ваниль", "28500", parfumShop.getId(),
                            "https://picsum.photos/seed/oud/600/400")
            ));
            log.info("✅ Загружено {} тестовых товаров для парфюмерии", productRepository.count());
        } else {
            log.info("Товары уже есть в БД, пропускаем загрузку");
        }

        /* Индексируем товары в Qdrant */
        aiServiceClient.indexProducts(productRepository.findAll());
        log.info("📦 Индексация товаров в Qdrant запущена");
    }

    private Product createProduct(String name, String description, String price, Long shopId, String imageUrl) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setActive(true);
        p.setShopId(shopId);
        p.setImageUrl(imageUrl);
        return p;
    }

}