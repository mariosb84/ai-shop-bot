package com.example.aishopbot.handler;

import org.telegram.telegrambots.meta.api.objects.Message;

public interface MessageHandler {
    void handleTextMessage(Message message);
}