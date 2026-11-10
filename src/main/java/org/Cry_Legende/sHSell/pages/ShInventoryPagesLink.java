package org.Cry_Legende.sHSell.pages;

import org.Cry_Legende.sHInventoryPages.api.ISHInventoryPagesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.IntConsumer;

/**
 * Pont vers SHInventoryPages. Cette classe n'est chargée que s'il est installé. L'API est relue à chaque appel :
 * SHInventoryPages retire son ancienne instance quand il est rechargé.
 */
public final class ShInventoryPagesLink implements PagesLink {

    private ShInventoryPagesLink() {
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHInventoryPages. */
    public static PagesLink open() {
        if (api() == null) throw new IllegalStateException("API de SHInventoryPages absente du ServicesManager");
        return new ShInventoryPagesLink();
    }

    private static ISHInventoryPagesAPI api() {
        return Bukkit.getServicesManager().load(ISHInventoryPagesAPI.class);
    }

    @Override
    public int applyToStoredPages(Player player, String operation, IntConsumer action) {
        ISHInventoryPagesAPI api = api();
        if (api == null || !api.isLoaded(player)) return 0;
        return api.applyToStoredPages(player, operation, action);
    }

    @Override
    public int freeSpace(Player player, ItemStack item) {
        ISHInventoryPagesAPI api = api();
        return api == null || !api.isLoaded(player) ? -1 : api.getFreeSpace(player, item);
    }

    @Override
    public boolean pagesOpen(Player player) {
        ISHInventoryPagesAPI api = api();
        return api == null || !api.isLoaded(player) || api.arePagesOpen(player);
    }

    @Override
    public void give(Player player, ItemStack item) {
        ISHInventoryPagesAPI api = api();
        if (api == null) {
            player.getInventory().addItem(item).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            return;
        }
        api.addItems(player, item).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
