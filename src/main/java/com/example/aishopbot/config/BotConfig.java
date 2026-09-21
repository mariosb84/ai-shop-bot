package com.example.aishopbot.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.example.aishopbot.bot.AiShopBot;

@Slf4j
@Configuration
public class BotConfig {

    @Value("${tg.username}")
    private String botUsername;

    @Value("${tg.token}")
    private String botToken;

    @Getter
    private static AiShopBot botInstance;

    public BotConfig(AiShopBot aiShopBot) {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(aiShopBot);
            botInstance = aiShopBot;
            log.info("✅ Бот {} зарегистрирован в Telegram", botUsername);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка регистрации бота", e);
        }
    }
}