package com.example.aishopbot.handler;

import com.example.aishopbot.bot.AiShopBot;
import com.example.aishopbot.client.AiServiceClient;
import com.example.aishopbot.repository.ProductRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageHandlerImpl implements MessageHandler {

    private final AiShopBot bot;
    private final UserStateManager stateManager;
    private final ProductRepository productRepository;
    private final AiServiceClient aiServiceClient;

    public MessageHandlerImpl(@Lazy AiShopBot bot,
                              UserStateManager stateManager,
                              ProductRepository productRepository,
                              AiServiceClient aiServiceClient) {
        this.bot = bot;
        this.stateManager = stateManager;
        this.productRepository = productRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Override
    public void handleTextMessage(Message message) {
        if (message == null || message.getText() == null) return;

        Long chatId = message.getChatId();
        String text = message.getText();
        String state = stateManager.getState(chatId);

        log.debug("Сообщение от {}: {} (state={})", chatId, text, state);

        // Если ждём вопрос для AI
        if (UserStateManager.STATE_ASK_AI.equals(state)) {
            handleAiQuestion(chatId, text);
            return;
        }

        switch (text) {
            case "/start" -> handleStart(chatId);
            default -> handleDefault(chatId);
        }
    }

    @Override
    public void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        log.debug("Callback от {}: {}", chatId, data);

        switch (data) {
            case "menu_catalog" -> showCatalog(chatId);
            case "menu_ai" -> startAiDialog(chatId);
            case "menu_help" -> bot.sendMessage(chatId, "📞 Если нужна помощь — напишите: support@example.com");
            case "ai_cancel" -> {
                stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
                bot.sendMessage(chatId, "Ок, возвращаемся в меню. Напишите /start");
            }
            default -> bot.sendMessage(chatId, "Неизвестная команда");
        }
    }

    private void handleStart(Long chatId) {
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("🛍 Каталог", "menu_catalog"));
        row1.add(createButton("💬 Спросить AI", "menu_ai"));
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("📞 Помощь", "menu_help"));
        rows.add(row2);

        keyboard.setKeyboard(rows);

        bot.sendMessageWithKeyboard(chatId,
                "👋 Здравствуйте! Это *AI-магазин*.\n\n" +
                        "Выберите действие:",
                keyboard);
    }

    private void handleDefault(Long chatId) {
        bot.sendMessage(chatId, "Пока я в разработке 🚧\nНапишите /start");
    }

    private void showCatalog(Long chatId) {
        var products = productRepository.findAllByActiveTrue();

        if (products.isEmpty()) {
            bot.sendMessage(chatId, "🛍 Каталог пока пуст.");
            return;
        }

        StringBuilder sb = new StringBuilder("🛍 *Наш каталог:*\n\n");
        for (var p : products) {
            sb.append("▪️ *").append(p.getName()).append("*\n")
                    .append("   ").append(p.getDescription()).append("\n")
                    .append("   💰 ").append(p.getPrice()).append(" ₽\n\n");
        }

        bot.sendMessage(chatId, sb.toString());
    }

    private void startAiDialog(Long chatId) {
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

    private void handleAiQuestion(Long chatId, String question) {
        stateManager.setState(chatId, UserStateManager.STATE_MAIN_MENU);
        bot.sendMessage(chatId, "🤔 Думаю над ответом...");
        String reply = aiServiceClient.ask(question, null);
        bot.sendMessage(chatId, reply);
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

}