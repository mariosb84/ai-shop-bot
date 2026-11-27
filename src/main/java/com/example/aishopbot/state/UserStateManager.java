package com.example.aishopbot.state;

import com.example.aishopbot.domain.UserState;
import com.example.aishopbot.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
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

    private final UserStateRepository repo;

    private UserState getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            UserState s = new UserState();
            s.setChatId(chatId);
            s.setState(STATE_SHOP_MENU);
            return s;
        });
    }

    public String getState(Long chatId) {
        return getOrCreate(chatId).getState();
    }

    public void setState(Long chatId, String state) {
        UserState s = getOrCreate(chatId);
        s.setState(state);
        repo.save(s);
    }

    public void clearState(Long chatId) {
        UserState s = getOrCreate(chatId);
        s.setState(STATE_SHOP_MENU);
        repo.save(s);
    }

    public void setSelectedShop(Long chatId, Long shopId) {
        UserState s = getOrCreate(chatId);
        s.setSelectedShopId(shopId);
        repo.save(s);
    }

    public Long getSelectedShop(Long chatId) {
        return getOrCreate(chatId).getSelectedShopId();
    }

    public void clearSelectedShop(Long chatId) {
        UserState s = getOrCreate(chatId);
        s.setSelectedShopId(null);
        repo.save(s);
    }

    public void setCatalogPage(Long chatId, int page) {
        UserState s = getOrCreate(chatId);
        s.setCatalogPage(page);
        repo.save(s);
    }

    public int getCatalogPage(Long chatId) {
        Integer p = getOrCreate(chatId).getCatalogPage();
        return p != null ? p : 0;
    }

    public void clearCatalogPage(Long chatId) {
        setCatalogPage(chatId, 0);
    }

    public boolean hasSelectedShop(Long chatId) {
        return getOrCreate(chatId).getSelectedShopId() != null;
    }

    public void setPendingProductId(Long chatId, Long productId) {
        UserState s = getOrCreate(chatId);
        s.setPendingProductId(productId);
        repo.save(s);
    }

    public Long getPendingProductId(Long chatId) {
        return getOrCreate(chatId).getPendingProductId();
    }

    public void clearPendingProductId(Long chatId) {
        UserState s = getOrCreate(chatId);
        s.setPendingProductId(null);
        repo.save(s);
    }

    public void setPendingName(Long chatId, String name) {
        UserState s = getOrCreate(chatId);
        s.setPendingName(name);
        repo.save(s);
    }

    public String getPendingName(Long chatId) {
        return getOrCreate(chatId).getPendingName();
    }

    public void setPendingPhone(Long chatId, String phone) {
        UserState s = getOrCreate(chatId);
        s.setPendingPhone(phone);
        repo.save(s);
    }

    public String getPendingPhone(Long chatId) {
        return getOrCreate(chatId).getPendingPhone();
    }

    public void clearPendingOrderData(Long chatId) {
        UserState s = getOrCreate(chatId);
        s.setPendingProductId(null);
        s.setPendingName(null);
        s.setPendingPhone(null);
        repo.save(s);
    }
}