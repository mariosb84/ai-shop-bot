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

    public static final String STATE_ADMIN_MENU = "ADMIN_MENU";
    public static final String STATE_ADMIN_UPLOAD_CATALOG = "ADMIN_UPLOAD_CATALOG";
    public static final String STATE_ADMIN_SET_PHONE = "ADMIN_SET_PHONE";

    public static final String STATE_ORDER_NAME = "ORDER_NAME";
    public static final String STATE_ORDER_PHONE = "ORDER_PHONE";
    public static final String STATE_ORDER_COMMENT = "ORDER_COMMENT";

    private final Map<Long, Integer> catalogPages = new ConcurrentHashMap<>();
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> selectedShop = new ConcurrentHashMap<>();

    private final Map<Long, Long> pendingProductId = new ConcurrentHashMap<>();

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

    public void setCatalogPage(Long chatId, int page) {
        catalogPages.put(chatId, page);
    }

    public int getCatalogPage(Long chatId) {
        return catalogPages.getOrDefault(chatId, 0);
    }

    public void clearCatalogPage(Long chatId) {
        catalogPages.remove(chatId);
    }

    /* Проверить, выбран ли магазин */
    public boolean hasSelectedShop(Long chatId) {
        return selectedShop.containsKey(chatId);
    }

    public void setPendingProductId(Long chatId, Long productId) {
        pendingProductId.put(chatId, productId);
    }

    public Long getPendingProductId(Long chatId) {
        return pendingProductId.get(chatId);
    }

    public void clearPendingProductId(Long chatId) {
        pendingProductId.remove(chatId);
    }

}
