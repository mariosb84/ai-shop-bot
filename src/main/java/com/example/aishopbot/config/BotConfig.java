package com.example.aishopbot.config;


import com.example.aishopbot.bot.AiShopBot;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class BotConfig {

    private final AiShopBot aiShopBot;

    @Getter
    private static AiShopBot botInstance;

    public BotConfig(AiShopBot aiShopBot) {
        this.aiShopBot = aiShopBot;
    }

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(aiShopBot);
            botInstance = aiShopBot;
            log.info("✅ Бот {} зарегистрирован в Telegram", aiShopBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка регистрации бота", e);
        }
    }
}