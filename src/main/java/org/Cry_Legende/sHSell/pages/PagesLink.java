package org.Cry_Legende.sHSell.pages;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.IntConsumer;

/**
 * Ce dont SHSell a besoin de SHInventoryPages. Cette interface ne mentionne aucune de ses classes : le plugin la
 * manipule sans charger {@link ShInventoryPagesLink}, donc sans SHInventoryPages il ne se passe rien.
 */
public interface PagesLink {

    /** Sans SHInventoryPages : seul l'inventaire porté existe. */
    PagesLink NONE = new PagesLink() {
        @Override
        public int applyToStoredPages(Player player, String operation, IntConsumer action) {
            return 0;
        }

        @Override
        public int freeSpace(Player player, ItemStack item) {
            return -1;
        }

        @Override
        public void give(Player player, ItemStack item) {
            player.getInventory().addItem(item).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }

        @Override
        public boolean pagesOpen(Player player) {
            return true;
        }
    };

    /** Applique {@code action} à chaque autre page du joueur posée comme inventaire porté ; nombre de pages traitées. */
    int applyToStoredPages(Player player, String operation, IntConsumer action);

    /** Place pour {@code item} dans l'inventaire porté et les autres pages, ou -1 sans SHInventoryPages. */
    int freeSpace(Player player, ItemStack item);

    /** Donne en utilisant l'inventaire porté puis les autres pages ; un reste éventuel tombe aux pieds du joueur. */
    void give(Player player, ItemStack item);

    /** Faux quand l'inventaire porté appartient à un autre plugin (parcours, arène, monde interdit). */
    boolean pagesOpen(Player player);
}
