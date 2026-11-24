package com.example.aishopbot.handler;

import com.example.aishopbot.bot.AiShopBot;
import com.example.aishopbot.client.AiServiceClient;
import com.example.aishopbot.domain.AiChatResponse;
import com.example.aishopbot.domain.Shop;
import com.example.aishopbot.repository.ProductRepository;
import com.example.aishopbot.repository.ShopRepository;
import com.example.aishopbot.state.UserStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import com.example.aishopbot.domain.Product;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MessageHandlerImpl implements MessageHandler {

    private final AiShopBot bot;
    private final UserStateManager stateManager;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final AiServiceClient aiServiceClient;
    private static final int CARDS_PER_PAGE = 3;

    @org.springframework.beans.factory.annotation.Value("${app.multi-shop-enabled:false}")
    private boolean multiShopEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.admin-chat-ids:}")
    private String adminChatIdsRaw;

    public MessageHandlerImpl(@Lazy AiShopBot bot,
                              UserStateManager stateManager,
                              ProductRepository productRepository,
                              ShopRepository shopRepository,
                              AiServiceClient aiServiceClient) {
        this.bot = bot;
        this.stateManager = stateManager;
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.aiServiceClient = aiServiceClient;
    }

    /* Обработка текстовых сообщений*/
    @Override
    public void handleTextMessage(Message message) {
        if (message == null || message.getText() == null) return;

        Long chatId = message.getChatId();
        String text = message.getText();
        String state = stateManager.getState(chatId);

        log.debug("Сообщение от {}: {} (state={})", chatId, text, state);

        /* Защита: если пользователь в состоянии ASK_AI, но отправил команду — */
        /* обрабатываем как команду, а не как вопрос к AI*/
        boolean isCommand = text.startsWith("/");

        if (UserStateManager.STATE_ASK_AI.equals(state) && !isCommand) {
            handleAiQuestion(chatId, text);
            return;
        }

        if (UserStateManager.STATE_ADMIN_SET_PHONE.equals(state) && !isCommand) {
            saveOperatorPhone(chatId, text);
            return;
        }

        /* Обработка команд*/
        switch (text) {
            case "/start" -> {
                /* Если магазин уже выбран — не сбрасываем */
                if (!stateManager.hasSelectedShop(chatId)) {
                    showShopSelection(chatId);
                } else {
                    Long shopId = stateManager.getSelectedShop(chatId);
                    Shop shop = shopRepository.findById(shopId).orElse(null);
                    if (shop != null) {
                        bot.sendMessage(chatId, "👋 С возвращением в *" + shop.getName() + "*\n\n" +
                                "Напишите /catalog или /ai");
                    } else {
                        showShopSelection(chatId);
                    }
                }
            }
            case "/shop" -> {
                if (multiShopEnabled) {
                    showShopSelection(chatId);
                } else {
                    bot.sendMessage(chatId, "ℹ️ Выбор магазина отключён");
                }
            }
            case "/catalog" -> showCatalog(chatId);
            case "/ai" -> startAiDialog(chatId);
            case "/help" -> bot.sendMessage(chatId,
                    "📞 Если нужна помощь — напишите: support@example.com");

            case "/admin" -> showAdminMenu(chatId);

            default -> {
                if (UserStateManager.STATE_ASK_AI.equals(state)) {
                    stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
                }
                bot.sendMessage(chatId, "Выберите команду из меню 👇");
            }
        }
    }

    /* Обработка нажатий на inline-кнопки*/
    @Override
    public void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        log.debug("Callback от {}: {}", chatId, data);

        /* Выбор магазина (формат: shop_1, shop_2)*/
        if (data.startsWith("shop_")) {
            Long shopId = Long.parseLong(data.substring(5));
            selectShop(chatId, shopId);
            return;
        }

        /* Навигация по каталогу*/
        if (data.startsWith("catalog_page_")) {
            Long shopId = stateManager.getSelectedShop(chatId);
            if (shopId == null) {
                showShopSelection(chatId);
                return;
            }
            int page = Integer.parseInt(data.substring("catalog_page_".length()));
            stateManager.setCatalogPage(chatId, page);
            sendCatalogPage(chatId, shopId, page);
            return;
        }

       /* Покупка товара (заглушка — реализуем позже)*/
        if (data.startsWith("buy_")) {
            Long productId = Long.parseLong(data.substring(4));
            bot.sendMessage(chatId, "🛒 Оформление заказа скоро появится!");
            return;
        }

        /* Просмотр карточки товара*/
        if (data.startsWith("view_product_")) {
            Long productId = Long.parseLong(data.substring("view_product_".length()));
            var product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                bot.sendMessage(chatId, "❌ Товар не найден");
                return;
            }
            sendProductCard(chatId, product);
            return;
        }

       /* Продолжить диалог с AI*/
        if ("ai_continue".equals(data)) {
            stateManager.setState(chatId, UserStateManager.STATE_ASK_AI);
            bot.sendMessage(chatId, "💬 Задайте следующий вопрос:");
            return;
        }

       /* Завершить диалог*/
        if ("ai_finish".equals(data)) {
            stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
            bot.sendMessage(chatId, "✅ Диалог завершён. Выберите команду из меню 👇");
            return;
        }

        if (data.startsWith("admin_")) {
            handleAdminCallback(chatId, data);
            return;
        }

        switch (data) {
            case "menu_catalog" -> showCatalog(chatId);
            case "menu_ai" -> startAiDialog(chatId);
            case "menu_help" -> bot.sendMessage(chatId, "📞 Если нужна помощь — напишите: support@example.com");
            case "menu_change_shop" -> showShopSelection(chatId);
            case "ai_cancel" -> {
                stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
                bot.sendMessage(chatId, "❌ Отменено. Выберите команду из меню 👇");
            }
            default -> bot.sendMessage(chatId, "Неизвестная команда");
        }
    }

    /* Показ списка магазинов или автовыбор, если магазин один */
    private void showShopSelection(Long chatId) {
        /* Если мультимагазин выключен — автоматически выбираем единственный магазин */
        if (!multiShopEnabled) {
            List<Shop> shops = shopRepository.findAllByActiveTrue();
            if (shops.isEmpty()) {
                bot.sendMessage(chatId, "❌ Магазины не настроены");
                return;
            }
            if (shops.size() == 1) {
                selectShop(chatId, shops.get(0).getId());
                return;
            }
            /* Если магазинов несколько, но флаг выключен — берём первый */
            selectShop(chatId, shops.get(0).getId());
            return;
        }

        /* Мультимагазин включён — показываем выбор */
        stateManager.setState(chatId, UserStateManager.STATE_SHOP_MENU);
        stateManager.clearSelectedShop(chatId);

        bot.setMenuButton(chatId);

        List<Shop> shops = shopRepository.findAllByActiveTrue();
        if (shops.isEmpty()) {
            bot.sendMessage(chatId, "❌ Магазины не настроены");
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Shop shop : shops) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton("🛍 " + shop.getName(), "shop_" + shop.getId()));
            rows.add(row);
        }

        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId,
                "👋 Здравствуйте! Выберите магазин:",
                keyboard);
    }

    /* Обработка выбора магазина */
    private void selectShop(Long chatId, Long shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) {
            bot.sendMessage(chatId, "❌ Магазин не найден");
            return;
        }

        stateManager.setSelectedShop(chatId, shopId);
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);

        /* Если мультимагазин включён — показываем подтверждение */
        if (multiShopEnabled) {
            bot.sendMessage(chatId, "✅ Вы в магазине *" + shop.getName() + "*\n\n" +
                    (shop.getDescription() != null ? shop.getDescription() : ""));
        } else {
            /* В режиме одного магазина — просто приветствие */
            bot.sendMessage(chatId, "👋 Здравствуйте! Добро пожаловать в *" + shop.getName() + "*\n\n" +
                    (shop.getDescription() != null ? shop.getDescription() : "") +
                    "\n\nНапишите /catalog или /ai");
        }

        bot.setMenuButton(chatId);
    }

    /* Показ каталога выбранного магазина*/

    /* Показ каталога — по 3 карточки за раз*/
    private void showCatalog(Long chatId) {
        Long shopId = stateManager.getSelectedShop(chatId);
        if (shopId == null) {
            /* Автовыбор, если магазин один */
            List<Shop> shops = shopRepository.findAllByActiveTrue();
            if (shops.size() == 1) {
                shopId = shops.get(0).getId();
                stateManager.setSelectedShop(chatId, shopId);
            } else {
                showShopSelection(chatId);
                return;
            }
        }

        stateManager.setCatalogPage(chatId, 0);
        sendCatalogPage(chatId, shopId, 0);
    }

    /* Отправка одной страницы каталога (3 товара)*/
    private void sendCatalogPage(Long chatId, Long shopId, int page) {
        var products = productRepository.findAllByShopIdAndActiveTrue(shopId);

        if (products.isEmpty()) {
            bot.sendMessage(chatId, "🛍 Каталог пока пуст.");
            return;
        }

        int totalPages = (int) Math.ceil(products.size() / (double) CARDS_PER_PAGE);
        int fromIndex = page * CARDS_PER_PAGE;
        int toIndex = Math.min(fromIndex + CARDS_PER_PAGE, products.size());

        if (fromIndex >= products.size()) {
            bot.sendMessage(chatId, "Больше товаров нет");
            return;
        }

        var pageProducts = products.subList(fromIndex, toIndex);

        bot.sendMessage(chatId, String.format("🛍 *Каталог* (страница %d из %d)",
                page + 1, totalPages));

        for (var p : pageProducts) {
            sendProductCard(chatId, p);
        }

        /* Кнопки навигации*/
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> navRow = new ArrayList<>();

        if (page > 0) {
            navRow.add(createButton("⬅️ Назад", "catalog_page_" + (page - 1)));
        }
        if (toIndex < products.size()) {
            navRow.add(createButton("Показать ещё ➡️", "catalog_page_" + (page + 1)));
        }

        if (!navRow.isEmpty()) {
            rows.add(navRow);
            keyboard.setKeyboard(rows);

            org.telegram.telegrambots.meta.api.methods.send.SendMessage msg =
                    new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("Навигация по каталогу:");
            msg.setReplyMarkup(keyboard);
            try {
                /* используем bot.sendMessageWithKeyboard*/
                bot.sendMessageWithKeyboard(chatId, "Навигация по каталогу:", keyboard);
            } catch (Exception ignored) {}
        }
    }

    /* Отправка одной карточки товара*/
    private void sendProductCard(Long chatId, com.example.aishopbot.domain.Product p) {
        StringBuilder caption = new StringBuilder();
        caption.append("*").append(p.getName()).append("*\n\n");
        if (p.getCategory() != null && !p.getCategory().isEmpty()) {
            caption.append("📂 ").append(p.getCategory()).append("\n\n");
        }
        if (p.getDescription() != null) {
            caption.append(p.getDescription()).append("\n\n");
        }
        caption.append("💰 *").append(p.getPrice()).append(" ₽*");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("🛒 Купить", "buy_" + p.getId()));
        rows.add(row);
        keyboard.setKeyboard(rows);

        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
            bot.sendPhoto(chatId, p.getImageUrl(), caption.toString(), keyboard);
        } else {
            bot.sendMessageWithKeyboard(chatId, caption.toString(), keyboard);
        }
    }

    /* Начало диалога с AI — переводим пользователя в состояние ожидания вопроса*/
    private void startAiDialog(Long chatId) {
        Long shopId = stateManager.getSelectedShop(chatId);
        if (shopId == null) {
            /* Автовыбор, если магазин один */
            List<Shop> shops = shopRepository.findAllByActiveTrue();
            if (shops.size() == 1) {
                shopId = shops.get(0).getId();
                stateManager.setSelectedShop(chatId, shopId);
            } else {
                showShopSelection(chatId);
                return;
            }
        }

        stateManager.setState(chatId, UserStateManager.STATE_ASK_AI);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("❌ Отмена", "ai_cancel"));
        rows.add(row);
        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId,
                "💬 *Задайте вопрос AI-консультанту*\n\n" +
                        "Например: «Что посоветуешь из одежды?»",
                keyboard);
    }

    /* Отправка вопроса в AI и получение ответа*/
    private void handleAiQuestion(Long chatId, String question) {
        Long shopId = stateManager.getSelectedShop(chatId);
        if (shopId == null) {
            showShopSelection(chatId);
            return;
        }

        bot.sendMessage(chatId, "🤔 Думаю над ответом...");

        AiChatResponse response = aiServiceClient.ask(question, null, shopId);

        /* Собираем кнопки*/
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        /* Кнопки с товарами (до 3)*/
        if (response.getProducts() != null && !response.getProducts().isEmpty()) {
            for (var p : response.getProducts()) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(createButton("🛍 " + p.getName(), "view_product_" + p.getId()));
                rows.add(row);
            }
        } else {
            /* Ничего не нашли — предложить каталог*/
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton("🛍 Каталог", "menu_catalog"));
            rows.add(row);
        }

        /* Кнопки управления диалогом*/
        List<InlineKeyboardButton> controlRow = new ArrayList<>();
        controlRow.add(createButton("💬 Продолжить", "ai_continue"));
        controlRow.add(createButton("❌ Завершить", "ai_finish"));
        rows.add(controlRow);

        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId, response.getReply(), keyboard);
    }

    private boolean isAdmin(Long chatId) {
        /* 1) по списку из конфига*/
        if (adminChatIdsRaw != null && !adminChatIdsRaw.isBlank()) {
            for (String id : adminChatIdsRaw.split(",")) {
                if (id.trim().equals(String.valueOf(chatId))) return true;
            }
        }
        /* 2) по adminChatId магазина*/
        Long shopId = stateManager.getSelectedShop(chatId);
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId).orElse(null);
            if (shop != null && chatId.equals(shop.getAdminChatId())) return true;
        }
        return false;
    }

    private void showAdminMenu(Long chatId) {
        if (!isAdmin(chatId)) {
            bot.sendMessage(chatId, "⛔ Нет доступа");
            return;
        }
        stateManager.setState(chatId, UserStateManager.STATE_ADMIN_MENU);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("📤 Загрузить каталог", "admin_upload")));
        rows.add(List.of(createButton("📞 Телефон оператора", "admin_phone")));
        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId, "🛠 *Админ-панель*", keyboard);
    }

    private void handleAdminCallback(Long chatId, String data) {
        if (!isAdmin(chatId)) {
            bot.sendMessage(chatId, "⛔ Нет доступа");
            return;
        }
        switch (data) {
            case "admin_upload" -> {
                stateManager.setState(chatId, UserStateManager.STATE_ADMIN_UPLOAD_CATALOG);
                bot.sendMessage(chatId, "📤 Отправьте Excel-файл (.xlsx)");
            }
            case "admin_phone" -> {
                stateManager.setState(chatId, UserStateManager.STATE_ADMIN_SET_PHONE);
                bot.sendMessage(chatId, "📞 Отправьте номер телефона оператора");
            }
            case "admin_back" -> showAdminMenu(chatId);
        }
    }

    private void saveOperatorPhone(Long chatId, String phone) {
        Long shopId = stateManager.getSelectedShop(chatId);
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) return;
        shop.setOperatorPhone(phone.trim());
        shopRepository.save(shop);
        stateManager.setState(chatId, UserStateManager.STATE_ADMIN_MENU);
        bot.sendMessage(chatId, "✅ Телефон сохранён: " + phone.trim());
    }

    @Override
    public void handleDocument(Message message) {
        Long chatId = message.getChatId();
        String state = stateManager.getState(chatId);

        if (!UserStateManager.STATE_ADMIN_UPLOAD_CATALOG.equals(state) || !isAdmin(chatId)) {
            bot.sendMessage(chatId, "⛔ Загрузка каталога доступна только админу в режиме загрузки");
            return;
        }

        try {
            var doc = message.getDocument();
            String filename = doc.getFileName() != null ? doc.getFileName() : "catalog.xlsx";
            if (!filename.toLowerCase().endsWith(".xlsx")) {
                bot.sendMessage(chatId, "❌ Нужен файл .xlsx");
                return;
            }

            byte[] bytes;
            try {
                var tgFile = bot.execute(new org.telegram.telegrambots.meta.api.methods.GetFile(doc.getFileId()));
                String fileUrl = "https://api.telegram.org/file/bot" + bot.getBotToken() + "/" + tgFile.getFilePath();
                try (var in = new java.net.URL(fileUrl).openStream()) {
                    bytes = in.readAllBytes();
                }
            } catch (Exception e) {
                log.error("Ошибка скачивания файла: {}", e.getMessage(), e);
                bot.sendMessage(chatId, "❌ Не удалось скачать файл");
                return;
            }

            Long shopId = stateManager.getSelectedShop(chatId);
            JsonNode resp = aiServiceClient.uploadCatalog(shopId, bytes, filename);
            if (resp == null) {
                bot.sendMessage(chatId, "❌ Ошибка загрузки каталога");
                return;
            }

            productRepository.deleteAll(productRepository.findAllByShopIdAndActiveTrue(shopId));

            List<Product> saved = new ArrayList<>();
            for (JsonNode p : resp.get("products")) {
                Product prod = new Product();
                prod.setName(p.get("name").asText());
                prod.setCategory(p.has("category") ? p.get("category").asText() : "");
                prod.setDescription(p.has("description") ? p.get("description").asText() : "");
                prod.setPrice(java.math.BigDecimal.valueOf(p.get("price").asDouble()));
                prod.setImageUrl(p.has("image_url") ? p.get("image_url").asText() : "");
                prod.setShopId(shopId);
                prod.setActive(true);
                saved.add(prod);
            }
            saved = productRepository.saveAll(saved);
            aiServiceClient.indexProducts(saved);

            stateManager.setState(chatId, UserStateManager.STATE_ADMIN_MENU);
            bot.sendMessage(chatId, "✅ Загружено " + saved.size() + " товаров");
        } catch (Exception e) {
            log.error("Ошибка загрузки каталога: {}", e.getMessage(), e);
            bot.sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    /* Вспомогательный метод создания inline-кнопки*/
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

}
