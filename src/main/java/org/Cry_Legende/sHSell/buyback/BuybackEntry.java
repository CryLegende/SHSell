package org.Cry_Legende.sHSell.buyback;

import org.bukkit.inventory.ItemStack;

/**
 * Objets vendus d'un même genre ({@code isSimilar}), rachetables par le vendeur. Les ventes successives du même objet
 * s'ajoutent à la même entrée : quantité et argent reçu cumulés, délai repoussé. Modifiée seulement sous le verrou de
 * la liste du joueur ({@link BuybackStore}).
 *
 * @see BuybackStore
 */
final class BuybackEntry {

    final String id;
    /** L'objet, quantité 1 ; jamais modifié. */
    final ItemStack template;
    /** Monnaie de la boutique (EconomyType de ShopGUIPlus). */
    final String economy;
    int amount;
    /** Argent reçu pour {@link #amount} objets : c'est le prix du rachat. */
    double price;
    long soldAt;
    long expiresAt;

    BuybackEntry(String id, ItemStack template, String economy, int amount, double price, long soldAt, long expiresAt) {
        this.id = id;
        this.template = template;
        this.economy = economy;
        this.amount = amount;
        this.price = price;
        this.soldAt = soldAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Prix de {@code count} objets : la part de l'argent reçu, arrondie au centime ; le dernier lot paie exactement ce
     * qui reste, si bien que tout racheter coûte toujours ce que la vente a rapporté.
     */
    double priceOf(int count) {
        if (count >= amount) return price;
        return Math.round(price * count / amount * 100.0) / 100.0;
    }

    /** Copie figée pour l'affichage et la sauvegarde. */
    Snapshot snapshot() {
        return new Snapshot(id, template, economy, amount, price, soldAt, expiresAt);
    }

    record Snapshot(String id, ItemStack template, String economy, int amount, double price, long soldAt, long expiresAt) {
    }
}
