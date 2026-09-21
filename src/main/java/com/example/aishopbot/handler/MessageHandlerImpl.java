package com.example.aishopbot.handler;

import com.example.aishopbot.bot.AiShopBot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

@Slf4j
@Service
/*@RequiredArgsConstructor*/
public class MessageHandlerImpl implements MessageHandler {

    private final AiShopBot bot;

    public MessageHandlerImpl(@Lazy AiShopBot bot) {
        this.bot = bot;
    }

    @Override
    public void handleTextMessage(Message message) {
        if (message == null || message.getText() == null) return;

        Long chatId = message.getChatId();
        String text = message.getText();

        log.debug("Сообщение от {}: {}", chatId, text);

        switch (text) {
            case "/start" -> bot.sendMessage(chatId,
                    "👋 Здравствуйте! Это AI-магазин.\n\n" +
                            "Я умею:\n" +
                            "🛍 Показывать каталог\n" +
                            "💬 Отвечать на вопросы через AI\n\n" +
                            "Пока что я в разработке 🚧");
            default -> bot.sendMessage(chatId, "В разработке 🚧");
        }
    }
}