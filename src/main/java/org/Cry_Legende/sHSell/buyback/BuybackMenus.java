package org.Cry_Legende.sHSell.buyback;

import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.kyori.adventure.text.Component;
import org.Cry_Legende.sHSell.SHSell;
import org.Cry_Legende.sHSell.util.ActionLog;
import org.Cry_Legende.sHSell.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Menu de rachat (/rachat) : les ventes récentes du joueur, de la plus récente à la plus ancienne. Clic : racheter une
 * pile au prix qu'elle a rapporté ; shift + clic : tout racheter. Les objets reviennent dans l'inventaire porté, puis
 * dans les autres pages d'inventaire (SHInventoryPages). Un membre du staff peut consulter le menu d'un joueur sans
 * rien y racheter.
 */
public final class BuybackMenus implements Listener {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private static final long CLICK_COOLDOWN_MS = 250L;

    private final SHSell plugin;
    private final BuybackStore store;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public BuybackMenus(SHSell plugin, BuybackStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Menu ouvert : ventes de {@code owner}, page {@code page}, affichées à son propriétaire ou au staff. */
    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final String ownerName;
        final boolean readOnly;
        int page;
        int pageCount;
        List<BuybackEntry.Snapshot> shown = List.of();
        Inventory inventory;

        Menu(UUID owner, String ownerName, boolean readOnly, int page) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.readOnly = readOnly;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player viewer, UUID owner, String ownerName, boolean readOnly, int page) {
        Texts texts = plugin.texts();
        List<BuybackEntry.Snapshot> entries = store.list(owner);
        if (entries.isEmpty()) {
            texts.send(viewer, readOnly ? "buyback-empty-other" : "buyback-empty", "%player%", ownerName);
            return;
        }
        Menu menu = new Menu(owner, ownerName, readOnly, page);
        menu.pageCount = pageCount(entries.size());
        menu.page = Math.max(0, Math.min(page, menu.pageCount - 1));
        menu.inventory = Bukkit.createInventory(menu, 54, texts.get(readOnly ? "buyback-title-other" : "buyback-title",
            "%player%", ownerName, "%page%", String.valueOf(menu.page + 1), "%max%", String.valueOf(menu.pageCount)));
        render(menu, entries);
        viewer.openInventory(menu.inventory);
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE);
    }

    /** Remplit le menu ; rouvert si le nombre de pages a changé (titre), fermé s'il n'y a plus rien. */
    private void render(Menu menu, List<BuybackEntry.Snapshot> entries) {
        Texts texts = plugin.texts();
        Inventory inv = menu.inventory;
        int from = menu.page * PER_PAGE;
        int to = Math.min(entries.size(), from + PER_PAGE);
        menu.shown = from < to ? List.copyOf(entries.subList(from, to)) : List.of();
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < PER_PAGE; slot++) {
            inv.setItem(slot, slot < menu.shown.size() ? icon(menu, menu.shown.get(slot), now) : null);
        }
        inv.setItem(SLOT_PREVIOUS, menu.page > 0 ? button(Material.ARROW, texts.raw("buyback-button-previous"), List.of()) : null);
        inv.setItem(SLOT_NEXT, menu.page < menu.pageCount - 1 ? button(Material.ARROW, texts.raw("buyback-button-next"), List.of()) : null);
        inv.setItem(SLOT_INFO, button(Material.BOOK, texts.raw("buyback-info-name"),
            texts.rawList(menu.readOnly ? "buyback-info-lore-other" : "buyback-info-lore",
                "%minutes%", String.valueOf(plugin.getConfig().getInt("buyback.duration-minutes", 60)))));
    }

    private ItemStack icon(Menu menu, BuybackEntry.Snapshot entry, long now) {
        Texts texts = plugin.texts();
        ItemStack icon = entry.template().clone();
        int stack = Math.max(1, entry.template().getMaxStackSize());
        icon.setAmount(Math.min(entry.amount(), stack));
        int firstLot = Math.min(entry.amount(), stack);
        double firstLotPrice = firstLot >= entry.amount() ? entry.price() : Math.round(entry.price() * firstLot / entry.amount() * 100.0) / 100.0;
        List<String> lines = texts.rawList(menu.readOnly ? "buyback-entry-lore-other" : "buyback-entry-lore",
            "%quantity%", plugin.formatAmount(entry.amount()),
            "%price%", plugin.formatPrice(entry.economy(), entry.price()),
            "%stack%", plugin.formatAmount(firstLot),
            "%stack_price%", plugin.formatPrice(entry.economy(), firstLotPrice),
            "%time%", duration(entry.expiresAt() - now));
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
        lore.addAll(Texts.items(lines));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        Texts texts = plugin.texts();
        if (seconds >= 3600) {
            return texts.raw("time-hours", "%h%", String.valueOf(seconds / 3600), "%m%", String.format("%02d", (seconds % 3600) / 60));
        }
        return texts.raw("time-minutes", "%m%", String.valueOf(seconds / 60), "%s%", String.format("%02d", seconds % 60));
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Texts.item(name));
        if (!lore.isEmpty()) meta.lore(Texts.items(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** Toutes les secondes : comptes à rebours et contenu des menus ouverts. */
    public void refreshOpen() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) refresh(viewer, menu);
        }
    }

    private void refresh(Player viewer, Menu menu) {
        List<BuybackEntry.Snapshot> entries = store.list(menu.owner);
        if (entries.isEmpty()) {
            viewer.closeInventory();
            return;
        }
        int pages = pageCount(entries.size());
        if (pages != menu.pageCount) {
            open(viewer, menu.owner, menu.ownerName, menu.readOnly, Math.min(menu.page, pages - 1));
            return;
        }
        render(menu, entries);
    }

    /** Arrêt du plugin : les menus ouverts sont fermés (leurs écouteurs disparaissent avec lui). */
    public void closeAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) viewer.closeInventory();
        }
    }

    // =========================================================================
    //  CLICS
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        // Clics simples seulement, un par quart de seconde : un double clic ne rachète jamais deux lots
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        long now = System.currentTimeMillis();
        Long last = lastClick.put(viewer.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        int slot = event.getRawSlot();
        if (slot == SLOT_PREVIOUS && menu.page > 0) {
            open(viewer, menu.owner, menu.ownerName, menu.readOnly, menu.page - 1);
        } else if (slot == SLOT_NEXT && menu.page < menu.pageCount - 1) {
            open(viewer, menu.owner, menu.ownerName, menu.readOnly, menu.page + 1);
        } else if (slot < PER_PAGE && slot < menu.shown.size() && !menu.readOnly && viewer.getUniqueId().equals(menu.owner)) {
            buy(viewer, menu, menu.shown.get(slot), event.isShiftClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    /**
     * Rachat : la place est vérifiée d'abord, puis le lot est retiré de l'entrée (un double clic ne trouve plus rien),
     * puis payé ; un paiement refusé remet le lot à sa place. Les objets ne sont donnés qu'une fois payés.
     */
    private void buy(Player player, Menu menu, BuybackEntry.Snapshot shown, boolean everything) {
        Texts texts = plugin.texts();
        int stack = Math.max(1, shown.template().getMaxStackSize());
        int wanted = everything ? shown.amount() : Math.min(shown.amount(), stack);
        // Inventaire porté d'un autre plugin (arène, parcours) : les objets disparaîtraient quand il le remettrait
        if (!plugin.pages().pagesOpen(player) || !plugin.canSellHere(player)) {
            texts.send(player, "buyback-unavailable-here");
            return;
        }
        EconomyProvider economy = plugin.economy(shown.economy());
        if (economy == null) {
            texts.send(player, "buyback-economy-missing");
            return;
        }
        if (freeSpace(player, shown.template()) < wanted) {
            texts.send(player, "buyback-no-space", "%quantity%", plugin.formatAmount(wanted));
            return;
        }
        BuybackStore.Taken taken = store.take(player.getUniqueId(), shown.id(), wanted);
        if (taken == null) {
            refresh(player, menu);
            return;
        }
        if (!economy.has(player, taken.cost())) {
            store.putBack(player.getUniqueId(), taken);
            texts.send(player, "buyback-no-money", "%price%", plugin.formatPrice(taken.economy(), taken.cost()));
            return;
        }
        try {
            economy.withdraw(player, taken.cost());
        } catch (RuntimeException e) {
            store.putBack(player.getUniqueId(), taken);
            plugin.getLogger().log(Level.SEVERE, "Paiement du rachat de " + player.getName() + " refusé", e);
            texts.send(player, "buyback-error");
            return;
        }
        ItemStack items = taken.template().clone();
        items.setAmount(taken.count());
        try {
            plugin.pages().give(player, items);
        } catch (RuntimeException | LinkageError e) {
            // Rien n'a pu être donné : argent rendu et lot remis
            economy.deposit(player, taken.cost());
            store.putBack(player.getUniqueId(), taken);
            plugin.getLogger().log(Level.SEVERE, "Rachat de " + player.getName() + " non livré, remboursé", e);
            texts.send(player, "buyback-error");
            return;
        }
        String itemName = Texts.plainName(taken.template());
        ActionLog.write(plugin, plugin.getConfig().getString("logs.buyback.file", "rachats"),
            plugin.getConfig().getString("logs.buyback.format", "%player% rachète %amount% %item% pour %price%")
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(taken.count()))
                .replace("%item%", itemName)
                .replace("%price%", plugin.formatPrice(taken.economy(), taken.cost())));
        Sound sound = plugin.buybackSound();
        if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        texts.sendWithItem(player, "buyback-done", taken.template(), "%quantity%", plugin.formatAmount(taken.count()),
            "%price%", plugin.formatPrice(taken.economy(), taken.cost()));
        refresh(player, menu);
    }

    /** Place pour {@code template} : inventaire porté et autres pages avec SHInventoryPages, sinon inventaire porté. */
    private int freeSpace(Player player, ItemStack template) {
        int pages = plugin.pages().freeSpace(player, template);
        if (pages >= 0) return pages;
        int stack = Math.max(1, template.getMaxStackSize());
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.isEmpty()) free += stack;
            else if (item.isSimilar(template)) free += Math.max(0, stack - item.getAmount());
        }
        return free;
    }
}
