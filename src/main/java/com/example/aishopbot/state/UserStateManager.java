package com.example.aishopbot.state;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStateManager {

    public static final String STATE_MAIN_MENU = "MAIN_MENU";
    public static final String STATE_SHOP_MENU = "SHOP_MENU";
    public static final String STATE_CATALOG = "CATALOG";
    public static final String STATE_ASK_AI = "ASK_AI";

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> selectedShop = new ConcurrentHashMap<>();

    public String getState(Long chatId) {
        return userStates.getOrDefault(chatId, STATE_SHOP_MENU);
    }

    public void setState(Long chatId, String state) {
        userStates.put(chatId, state);
    }

    public void clearState(Long chatId) {
        userStates.remove(chatId);
    }

    public void setSelectedShop(Long chatId, Long shopId) {
        selectedShop.put(chatId, shopId);
    }

    public Long getSelectedShop(Long chatId) {
        return selectedShop.get(chatId);
    }

    public void clearSelectedShop(Long chatId) {
        selectedShop.remove(chatId);
    }
}