package com.example.aishopbot.bot;

import com.example.aishopbot.handler.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

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
        } else if (update.hasCallbackQuery()) {
            messageHandler.handleCallback(update.getCallbackQuery());
        }
    }

    /* Отправка обычного сообщения */
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

    /* Отправка сообщения с inline-клавиатурой */
    public void sendMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопками: {}", e.getMessage());
        }
    }

    /* Установка меню-кнопки (гамбургер в левом нижнем углу).
       Telegram сам показывает кнопку меню, когда есть список команд.
       Достаточно один раз установить список команд для бота. */
    public void setMenuButton(Long chatId) {
        try {
            List<BotCommand> commands = List.of(
                    new BotCommand("start", "🏠 Главное меню"),
                    new BotCommand("shop", "🔄 Сменить магазин"),
                    new BotCommand("catalog", "🛍 Каталог"),
                    new BotCommand("ai", "💬 Спросить AI"),
                    new BotCommand("help", "📞 Помощь")
            );
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            log.info("Меню-кнопка установлена для chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка установки меню: {}", e.getMessage());
        }
    }

}