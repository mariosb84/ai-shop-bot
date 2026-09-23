package com.example.aishopbot.handler;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

public interface MessageHandler {
    void handleTextMessage(Message message);

    void handleCallback(CallbackQuery callbackQuery);

}