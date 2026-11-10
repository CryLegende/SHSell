package org.Cry_Legende.sHSell.shop;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès à ShopGUIPlus. La vente « tout vendre » d'un objet ({@code ShopManager#handleItemSellAll}) et les réglages de
 * /sell all ({@code Settings}) ne font pas partie de son API publiée : ils sont résolus une fois au démarrage, et
 * /sell allpages reste coupé si une mise à jour de ShopGUIPlus les a changés. Passer par handleItemSellAll garde tout
 * ce que fait /sell all : prix et multiplicateurs du joueur, événements de transaction, journal shop.log, son et
 * commandes de vente des objets.
 */
public final class ShopBridge {

    private static final List<String> SETTINGS = List.of("sellAllExcludeFreeItems", "sellAllExcludeArmorSlots",
        "sellAllExcludeOffHand", "disableShopsInWorlds", "disableShopsInGamemodes");

    private final ShopGuiPlugin shopPlugin;
    private final MethodHandle handleItemSellAll;
    /** Champs statiques de Settings, relus à chaque usage : un /shop reload les met à jour. */
    private final Map<String, Field> settings;

    private ShopBridge(ShopGuiPlugin shopPlugin, MethodHandle handleItemSellAll, Map<String, Field> settings) {
        this.shopPlugin = shopPlugin;
        this.handleItemSellAll = handleItemSellAll;
        this.settings = settings;
    }

    public static ShopBridge open() throws ReflectiveOperationException {
        ShopGuiPlugin shopPlugin = ShopGuiPlusApi.getPlugin();
        if (shopPlugin == null) throw new IllegalStateException("ShopGUIPlus n'est pas chargé");
        MethodHandle sellAll = MethodHandles.publicLookup().findVirtual(ShopManager.class, "handleItemSellAll",
            MethodType.methodType(ShopTransactionResult.class, Player.class, ShopItem.class, boolean.class));
        Class<?> settingsClass = Class.forName("net.brcdev.shopgui.config.Settings", true, shopPlugin.getClass().getClassLoader());
        Map<String, Field> fields = new HashMap<>();
        for (String name : SETTINGS) {
            Field field = settingsClass.getField(name);
            // Lecture d'essai : un champ renommé est vu au démarrage, pas au premier /sell allpages
            field.get(null);
            fields.put(name, field);
        }
        return new ShopBridge(shopPlugin, sellAll, Map.copyOf(fields));
    }

    public boolean isPlayerLoaded(Player player) {
        return shopPlugin.getPlayerManager().isPlayerLoaded(player);
    }

    /** Objet du shop qui achète {@code item} à ce joueur, ou null. */
    public ShopItem findShopItem(Player player, ItemStack item) {
        return shopPlugin.getShopManager().findShopItemByItemStack(player, item, flag("sellAllExcludeFreeItems"));
    }

    /** Vend tous les exemplaires de cet objet de l'inventaire porté, comme /sell all (aucun message envoyé). */
    public ShopTransactionResult sellAll(Player player, ShopItem shopItem) throws Throwable {
        return (ShopTransactionResult) handleItemSellAll.invoke(shopPlugin.getShopManager(), player, shopItem, false);
    }

    public boolean excludesArmor() {
        return flag("sellAllExcludeArmorSlots");
    }

    public boolean excludesOffHand() {
        return flag("sellAllExcludeOffHand");
    }

    /** Monde où les boutiques sont fermées (disableShopsInWorlds). */
    public boolean isWorldDisabled(String world) {
        return value("disableShopsInWorlds") instanceof Collection<?> worlds && worlds.contains(world);
    }

    /** Mode de jeu où les boutiques sont fermées (disableShopsInGamemodes). */
    public boolean isGameModeDisabled(GameMode mode) {
        return value("disableShopsInGamemodes") instanceof Collection<?> modes && modes.contains(mode);
    }

    private boolean flag(String name) {
        return Boolean.TRUE.equals(value(name));
    }

    private Object value(String name) {
        try {
            return settings.get(name).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
