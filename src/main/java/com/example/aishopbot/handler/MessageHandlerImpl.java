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
import com.example.aishopbot.domain.Order;
import com.example.aishopbot.repository.OrderRepository;

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

    private final OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Value("${app.multi-shop-enabled:false}")
    private boolean multiShopEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.admin-chat-ids:}")
    private String adminChatIdsRaw;

    public MessageHandlerImpl(@Lazy AiShopBot bot,
                              UserStateManager stateManager,
                              ProductRepository productRepository,
                              ShopRepository shopRepository,
                              OrderRepository orderRepository,
                              AiServiceClient aiServiceClient) {
        this.bot = bot;
        this.stateManager = stateManager;
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.orderRepository = orderRepository;
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

        if (UserStateManager.STATE_ORDER_NAME.equals(state) && !isCommand) {
            handleOrderName(chatId, text);
            return;
        }
        if (UserStateManager.STATE_ORDER_PHONE.equals(state) && !isCommand) {
            handleOrderPhone(chatId, text);
            return;
        }
        if (UserStateManager.STATE_ORDER_COMMENT.equals(state) && !isCommand) {
            handleOrderComment(chatId, text);
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

       /* Покупка товара */
        if (data.startsWith("buy_")) {
            Long productId = Long.parseLong(data.substring(4));
            startOrder(chatId, productId);
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

        if (data.startsWith("admin_") || data.startsWith("order_status_")) {
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
                        "Например: «Посоветуй парфюм на вечер» или «Есть что-то свежее?»",
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
        rows.add(List.of(createButton("📋 Текущие заказы", "admin_orders_new")));
        rows.add(List.of(createButton("🗄 Архив заказов", "admin_orders_archive")));
        rows.add(List.of(createButton("❌ Отмена", "admin_cancel")));
        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId, "🛠 *Админ-панель*", keyboard);
    }

    private void handleAdminCallback(Long chatId, String data) {

        log.info("ADMIN CALLBACK data={}", data);

        if (!isAdmin(chatId)) {
            bot.sendMessage(chatId, "⛔ Нет доступа");
            return;
        }

        if (data.startsWith("order_status_")) {
            String[] parts = data.split("_");
            Long orderId = Long.parseLong(parts[2]);
            String status = parts[3];
            updateOrderStatus(chatId, orderId, status);
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
            case "admin_cancel" -> {
                stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
                bot.sendMessage(chatId, "Выход из админ-панели");
            }
            case "admin_orders_new" -> showOrders(chatId, false);
            case "admin_orders_archive" -> showOrders(chatId, true);
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

            var oldProducts = productRepository.findAllByShopIdAndActiveTrue(shopId);
            for (var p : oldProducts) {
                p.setActive(false);
            }
            productRepository.saveAll(oldProducts);

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

    /* ==================== ОФОРМЛЕНИЕ ЗАКАЗА ==================== */

    private void startOrder(Long chatId, Long productId) {
        var product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            bot.sendMessage(chatId, "❌ Товар не найден");
            return;
        }
        stateManager.setPendingProductId(chatId, productId);
        stateManager.setState(chatId, UserStateManager.STATE_ORDER_NAME);
        bot.sendMessage(chatId, "📝 Оформление заказа\n\nКак вас зовут?");
    }

    private void handleOrderName(Long chatId, String name) {
        if (name.trim().isEmpty()) {
            bot.sendMessage(chatId, "Введите имя");
            return;
        }
        stateManager.setState(chatId, UserStateManager.STATE_ORDER_PHONE);
        bot.sendMessage(chatId, "📞 Ваш номер телефона?");
        /* имя сохраним во временное поле */
        stateManager.setPendingName(chatId, name.trim());
    }

    private void handleOrderPhone(Long chatId, String phone) {
        stateManager.setState(chatId, UserStateManager.STATE_ORDER_COMMENT);
        bot.sendMessage(chatId, "💬 Комментарий к заказу (или напишите «-», чтобы пропустить)");
        stateManager.setPendingPhone(chatId, phone.trim());
    }

    private void handleOrderComment(Long chatId, String comment) {
        Long productId = stateManager.getPendingProductId(chatId);
        Long shopId = stateManager.getSelectedShop(chatId);
        if (productId == null || shopId == null) {
            bot.sendMessage(chatId, "❌ Ошибка: товар не найден. Начните заново.");
            return;
        }

        var product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            bot.sendMessage(chatId, "❌ Товар не найден");
            return;
        }

        Shop shop = shopRepository.findById(shopId).orElse(null);

        Order order = new Order();
        order.setShopId(shopId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setProductPrice(product.getPrice());
        String cName = stateManager.getPendingName(chatId);
        String cPhone = stateManager.getPendingPhone(chatId);
        order.setCustomerName(cName != null ? cName : "—");
        order.setCustomerPhone(cPhone != null ? cPhone : "—");
        order.setCustomerComment("-".equals(comment.trim()) ? "" : comment.trim());
        order.setCustomerChatId(chatId);
        order.setStatus("new");
        order = orderRepository.save(order);

        /* Уведомление админу */

            StringBuilder msg = new StringBuilder();
            msg.append("🛍 *Новый заказ #").append(order.getId()).append("*\n\n");
            msg.append("Товар: ").append(product.getName()).append("\n");
            msg.append("Цена: ").append(product.getPrice()).append(" ₽\n");
            msg.append("Клиент: ").append(order.getCustomerName()).append("\n");
            msg.append("Телефон: ").append(order.getCustomerPhone()).append("\n");
            if (!order.getCustomerComment().isEmpty()) {
                msg.append("Комментарий: ").append(order.getCustomerComment()).append("\n");
            }
            msg.append("TG ID: ").append(chatId).append("\n");
            if (shop != null && shop.getOperatorPhone() != null) {
                msg.append("\nОператор: ").append(shop.getOperatorPhone());
            }

            /* Кому слать: adminChatId магазина, иначе — список из конфига */

            java.util.Set<Long> recipients = new java.util.HashSet<>();
            if (shop != null && shop.getAdminChatId() != null) {
                recipients.add(shop.getAdminChatId());
            }
            if (adminChatIdsRaw != null && !adminChatIdsRaw.isBlank()) {
                for (String id : adminChatIdsRaw.split(",")) {
                    try { recipients.add(Long.parseLong(id.trim())); } catch (NumberFormatException ignored) {}
                }
            }
            for (Long rid : recipients) {
                bot.sendMessage(rid, msg.toString());
            }

        /* Подтверждение клиенту */
        String confirm = "✅ Заказ #" + order.getId() + " оформлен!\n\n" +
                "Товар: " + product.getName() + "\n" +
                "Сумма: " + product.getPrice() + " ₽\n";
        if (shop != null && shop.getOperatorPhone() != null) {
            confirm += "Оператор свяжется с вами с этого номера телефона : " + shop.getOperatorPhone()  + "\n" +
                    "Либо можете сами позвонить, если вам удобно.";
        } else {
            confirm += "Оператор свяжется с вами в ближайшее время.";
        }
        bot.sendMessage(chatId, confirm);

        /* Очистка */
        stateManager.clearPendingOrderData(chatId);
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
    }

    /* ==================== СПИСОК ЗАКАЗОВ ==================== */

    private void showOrders(Long chatId, boolean archive) {
        Long shopId = stateManager.getSelectedShop(chatId);
        List<Order> all = (shopId != null)
                ? orderRepository.findAllByShopIdOrderByCreatedAtDesc(shopId)
                : orderRepository.findAllByOrderByCreatedAtDesc();

        List<Order> orders = all.stream()
                .filter(o -> archive
                        ? ("done".equals(o.getStatus()) || "cancelled".equals(o.getStatus()))
                        : "new".equals(o.getStatus()))
                .toList();

        if (orders.isEmpty()) {
            bot.sendMessage(chatId, archive ? "🗄 Архив пуст" : "📋 Текущих заказов нет");
            return;
        }

        int limit = Math.min(orders.size(), 10);
        bot.sendMessage(chatId, (archive ? "🗄 *Архив заказов*" : "📋 *Текущие заказы*")
                + " (" + orders.size() + " всего)");

        for (int i = 0; i < limit; i++) {
            Order o = orders.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append("*Заказ #").append(o.getId()).append("* [").append(statusRu(o.getStatus())).append("]\n");
            sb.append("Товар: ").append(o.getProductName()).append("\n");
            sb.append("Сумма: ").append(o.getProductPrice()).append(" ₽\n");
            sb.append("Клиент: ").append(o.getCustomerName()).append(", ").append(o.getCustomerPhone()).append("\n");
            if (o.getCustomerComment() != null && !o.getCustomerComment().isEmpty()) {
                sb.append("Комментарий: ").append(o.getCustomerComment()).append("\n");
            }
            sb.append("TG: ").append(o.getCustomerChatId()).append("\n");
            sb.append("Дата: ").append(o.getCreatedAt()).append("\n");

            if (!archive) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(createButton("✅ Выполнен", "order_status_" + o.getId() + "_done"));
                row.add(createButton("❌ Отменён", "order_status_" + o.getId() + "_cancelled"));
                rows.add(row);
                keyboard.setKeyboard(rows);
                bot.sendMessageWithKeyboard(chatId, sb.toString(), keyboard);
            } else {
                bot.sendMessage(chatId, sb.toString());
            }
        }

        bot.sendMessageWithKeyboard(chatId, "Вернуться в админку:",
                keyboardWith(List.of(createButton("⬅️ Назад", "admin_back"))));
    }

    private void updateOrderStatus(Long chatId, Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            bot.sendMessage(chatId, "❌ Заказ не найден");
            return;
        }
        order.setStatus(status);
        orderRepository.save(order);
        bot.sendMessage(chatId, "✅ Заказ #" + orderId + " — " + statusRu(status));
    }

    private String statusRu(String status) {
        return switch (status) {
            case "new" -> "новый";
            case "done" -> "выполнен";
            case "cancelled" -> "отменён";
            default -> status;
        };
    }

    private InlineKeyboardMarkup keyboardWith(List<InlineKeyboardButton> buttons) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(List.of(buttons));
        return kb;
    }

}
