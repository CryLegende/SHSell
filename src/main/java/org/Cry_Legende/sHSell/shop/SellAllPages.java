package org.Cry_Legende.sHSell.shop;

import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.Cry_Legende.sHSell.SHSell;
import org.Cry_Legende.sHSell.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * /sell allpages : /sell all sur l'inventaire porté, puis sur chacune des autres pages d'inventaire du joueur
 * (SHInventoryPages pose chaque page comme inventaire porté le temps de la vendre). Un seul message résume tout.
 */
public final class SellAllPages {

    private final SHSell plugin;
    private final ShopBridge shop;
    private final Map<UUID, Integer> lastUse = new ConcurrentHashMap<>();

    public SellAllPages(SHSell plugin, ShopBridge shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    /** Ventes cumulées d'un /sell allpages. */
    private static final class Totals {
        int items;
        final Map<String, Double> money = new TreeMap<>();

        void add(ShopItem shopItem, int amount, double price) {
            items += amount;
            money.merge(Economies.of(shopItem), price, Double::sum);
        }
    }

    public void execute(Player player) {
        Texts texts = plugin.texts();
        if (!player.hasPermission("shopguiplus.sell.all") || !player.hasPermission("shsell.allpages")) {
            texts.send(player, "no-permission");
            return;
        }
        if (shop.isGameModeDisabled(player.getGameMode()) || shop.isWorldDisabled(player.getWorld().getName())) {
            texts.send(player, "sell-unavailable-here");
            return;
        }
        int now = Bukkit.getCurrentTick();
        Integer last = lastUse.get(player.getUniqueId());
        int cooldown = plugin.getConfig().getInt("sell-allpages.cooldown-ticks", 40);
        if (last != null && now - last < cooldown) {
            texts.send(player, "sell-cooldown");
            return;
        }
        if (!shop.isPlayerLoaded(player)) {
            texts.send(player, "sell-loading");
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        Totals totals = new Totals();
        int pages = 0;
        try {
            // Pages d'abord : la copie gardée dans l'historique juste avant contient aussi l'inventaire porté intact
            pages = plugin.pages().applyToStoredPages(player, "sell_allpages", page -> sellInventory(player, totals, false));
            sellInventory(player, totals, true);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, "/sell allpages de " + player.getName() + " interrompu", e);
            texts.send(player, "sell-error");
        }
        if (totals.items == 0) {
            texts.send(player, "sell-nothing");
            return;
        }
        texts.send(player, "sell-done", "%quantity%", plugin.formatAmount(totals.items), "%price%", formatMoney(totals),
            "%pages%", String.valueOf(pages + 1));
    }

    public void forget(UUID uuid) {
        lastUse.remove(uuid);
    }

    /**
     * Même parcours que /sell all : chaque emplacement de l'inventaire porté, armure et main secondaire selon les
     * réglages de ShopGUIPlus. Pour une page posée comme inventaire porté, seuls ses 36 emplacements comptent :
     * l'armure et la main secondaire sont celles du joueur, déjà vendues au premier passage.
     */
    private void sellInventory(Player player, Totals totals, boolean wornInventory) {
        PlayerInventory inv = player.getInventory();
        boolean armor = wornInventory && !shop.excludesArmor();
        boolean offHand = wornInventory && !shop.excludesOffHand();
        int size = inv.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (slot >= 36 && slot <= 39 && !armor) continue;
            if (slot >= 40 && (slot > 40 || !offHand)) continue;
            // Relu à chaque tour : une vente « tout » retire aussi les exemplaires des emplacements suivants
            ItemStack item = inv.getItem(slot);
            if (item == null || item.isEmpty()) continue;
            ShopItem shopItem = shop.findShopItem(player, item);
            if (shopItem == null) continue;
            try {
                ShopTransactionResult result = shop.sellAll(player, shopItem);
                if (result != null && result.getResult() == ShopTransactionResult.ShopTransactionResultType.SUCCESS) {
                    totals.add(shopItem, result.getAmount(), result.getPrice());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException("Vente ShopGUIPlus échouée", t);
            }
        }
    }

    private String formatMoney(Totals totals) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : totals.money.entrySet()) {
            if (!text.isEmpty()) text.append(", ");
            text.append(plugin.formatPrice(entry.getKey(), entry.getValue()));
        }
        return text.toString();
    }
}
