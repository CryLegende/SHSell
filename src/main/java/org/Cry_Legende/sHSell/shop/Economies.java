package org.Cry_Legende.sHSell.shop;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.brcdev.shopgui.shop.item.ShopItem;

/**
 * Monnaie d'une boutique ShopGUIPlus. Une boutique sans monnaie propre (economyType absent de son fichier) paie dans
 * la monnaie par défaut, la première de economyTypes : elle est notée {@link #DEFAULT}.
 */
public final class Economies {

    public static final String DEFAULT = "DEFAULT";

    private Economies() {
    }

    /** Clé de la monnaie d'un objet du shop : nom de l'EconomyType, ou DEFAULT. */
    public static String of(ShopItem shopItem) {
        EconomyType type = shopItem.getShop().getEconomyType();
        return type == null ? DEFAULT : type.name();
    }

    /** Monnaie d'une clé, ou null si elle n'existe plus. */
    public static EconomyProvider provider(String key) {
        try {
            var manager = ShopGuiPlusApi.getPlugin().getEconomyManager();
            return DEFAULT.equals(key) ? manager.getDefaultEconomyProvider() : manager.getEconomyProvider(EconomyType.valueOf(key));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
