package com.example.aishopbot.handler;

import com.example.aishopbot.bot.AiShopBot;
import com.example.aishopbot.client.AiServiceClient;
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

        /* Обработка команд*/
        switch (text) {
            case "/start" -> showShopSelection(chatId);
            case "/shop" -> showShopSelection(chatId);
            case "/catalog" -> showCatalog(chatId);
            case "/ai" -> startAiDialog(chatId);
            case "/help" -> bot.sendMessage(chatId,
                    "📞 Если нужна помощь — напишите: support@example.com");
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

    /* Показ списка магазинов для выбора*/
    private void showShopSelection(Long chatId) {
        stateManager.setState(chatId, UserStateManager.STATE_SHOP_MENU);
        stateManager.clearSelectedShop(chatId);

        /* Устанавливаем меню-кнопку (гамбургер в левом нижнем углу)*/
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

    /* Обработка выбора магазина*/
    private void selectShop(Long chatId, Long shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) {
            bot.sendMessage(chatId, "❌ Магазин не найден");
            return;
        }

        stateManager.setSelectedShop(chatId, shopId);
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);

        bot.sendMessage(chatId, "✅ Вы в магазине *" + shop.getName() + "*\n\n" +
                (shop.getDescription() != null ? shop.getDescription() : ""));

        /* Обновляем меню-кнопку*/
        bot.setMenuButton(chatId);
    }

    /* Показ каталога выбранного магазина*/

    /* Показ каталога — по 3 карточки за раз*/
    private void showCatalog(Long chatId) {
        Long shopId = stateManager.getSelectedShop(chatId);
        if (shopId == null) {
            showShopSelection(chatId);
            return;
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
            showShopSelection(chatId);
            return;
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
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);

        if (shopId == null) {
            showShopSelection(chatId);
            return;
        }

        bot.sendMessage(chatId, "🤔 Думаю над ответом...");
        String reply = aiServiceClient.ask(question, null, shopId);
        bot.sendMessage(chatId, reply);
    }

    /* Вспомогательный метод создания inline-кнопки*/
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

}