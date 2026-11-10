package org.Cry_Legende.sHSell.buyback;

import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.Cry_Legende.sHSell.shop.Economies;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relève ce que chaque vente de ShopGUIPlus retire vraiment de l'inventaire. ShopGUIPlus vend toujours dans le même
 * ordre : événement d'avant, retrait des objets, paiement, événement d'après. L'état des emplacements concernés est
 * copié avant, comparé après : on garde les objets exacts (nom, enchantements, usure), pas le modèle de la boutique.
 * Une vente sans événement d'avant (addon SellGUI) n'est pas relevée.
 */
public final class SaleCapture implements Listener {

    private final BuybackStore store;
    /** Emplacements copiés à l'événement d'avant, par joueur, jusqu'à l'événement d'après. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(ShopItem shopItem, int[] slots, ItemStack[] before) {
    }

    public SaleCapture(BuybackStore store) {
        this.store = store;
    }

    private static boolean isSale(ShopManager.ShopAction action) {
        return action == ShopManager.ShopAction.SELL || action == ShopManager.ShopAction.SELL_ALL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreTransaction(ShopPreTransactionEvent event) {
        Player player = event.getPlayer();
        if (player == null || !isSale(event.getShopAction())) return;
        ItemStack model = event.getShopItem().getItem();
        if (model == null || store.isIgnored(model.getType())) {
            pending.remove(player.getUniqueId());
            return;
        }
        // Seuls les emplacements qui portent ce type d'objet peuvent perdre quelque chose dans cette vente
        Material type = model.getType();
        PlayerInventory inv = player.getInventory();
        int size = inv.getSize();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() != type) continue;
            slots.add(slot);
            before.add(item.clone());
        }
        int[] indexes = new int[slots.size()];
        for (int i = 0; i < indexes.length; i++) indexes[i] = slots.get(i);
        pending.put(player.getUniqueId(), new Pending(event.getShopItem(), indexes, before.toArray(new ItemStack[0])));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostTransaction(ShopPostTransactionEvent event) {
        ShopTransactionResult result = event.getResult();
        Player player = result.getPlayer();
        if (player == null) return;
        Pending sale = pending.remove(player.getUniqueId());
        if (sale == null || sale.shopItem() != result.getShopItem() || !isSale(result.getShopAction())
            || result.getResult() != ShopTransactionResult.ShopTransactionResultType.SUCCESS || result.getPrice() <= 0) {
            return;
        }
        // Objets retirés, regroupés par genre (isSimilar)
        PlayerInventory inv = player.getInventory();
        List<Removed> removed = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < sale.slots().length; i++) {
            ItemStack was = sale.before()[i];
            ItemStack now = inv.getItem(sale.slots()[i]);
            int left = now != null && now.isSimilar(was) ? now.getAmount() : 0;
            int gone = was.getAmount() - left;
            if (gone <= 0) continue;
            total += gone;
            Removed group = null;
            for (Removed seen : removed) {
                if (seen.template.isSimilar(was)) {
                    group = seen;
                    break;
                }
            }
            if (group == null) {
                group = new Removed(was.asOne());
                removed.add(group);
            }
            group.count += gone;
        }
        if (total <= 0) return;
        String economy = Economies.of(result.getShopItem());
        double price = result.getPrice();
        for (Removed group : removed) {
            store.add(player.getUniqueId(), group.template, group.count, price * group.count / total, economy);
        }
    }

    /** Objets d'un même genre retirés par une vente. */
    private static final class Removed {
        final ItemStack template;
        int count;

        Removed(ItemStack template) {
            this.template = template;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
