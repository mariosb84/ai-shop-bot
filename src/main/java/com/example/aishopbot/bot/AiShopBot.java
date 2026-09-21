package com.example.aishopbot.bot;

import com.example.aishopbot.handler.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class AiShopBot extends TelegramLongPollingBot {

    private final MessageHandler messageHandler;

    @Value("${tg.username}")
    private String botUsername;

    @Value("${tg.token}")
    private String botToken;

    public AiShopBot(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            messageHandler.handleTextMessage(update.getMessage());
        }
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }
}