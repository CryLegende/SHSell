package org.Cry_Legende.sHSell.buyback;

import org.Cry_Legende.sHSell.SHSell;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Ventes récentes de chaque joueur, rachetables pendant {@code buyback.duration-minutes}. Une vente de masse ne remplit
 * pas le menu : les ventes du même objet s'additionnent dans une seule entrée, les objets de {@code buyback.ignored}
 * ne sont pas gardés, et au-delà de {@code buyback.max-entries} entrées c'est la moins chère qui part.
 *
 * <p>Les ventes arrivent du fil du monde du joueur (mondes tiqués en parallèle) ; la liste d'un joueur n'est lue et
 * modifiée que sous son propre verrou. Fichier rachats.yml, écrit hors du thread principal quand il a changé.</p>
 */
public final class BuybackStore {

    private final SHSell plugin;
    private final File file;
    private final Map<UUID, List<BuybackEntry>> players = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Object fileLock = new Object();
    private volatile long durationMillis;
    private volatile int maxEntries;
    private volatile Set<Material> ignored = Set.of();

    public BuybackStore(SHSell plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rachats.yml");
    }

    public void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        durationMillis = TimeUnit.MINUTES.toMillis(Math.max(1, config.getInt("buyback.duration-minutes", 60)));
        maxEntries = Math.max(1, config.getInt("buyback.max-entries", 45));
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("buyback.ignored")) {
            Material material = Material.matchMaterial(name);
            if (material != null) materials.add(material);
            else plugin.getLogger().warning("buyback.ignored : objet inconnu « " + name + " »");
        }
        ignored = Set.copyOf(materials);
    }

    // =========================================================================
    //  VENTES ET RACHATS
    // =========================================================================

    /** Objets qu'un joueur vient de vendre (template de quantité 1). */
    public void add(UUID player, ItemStack template, int amount, double price, String economy) {
        if (amount <= 0 || price < 0 || ignored.contains(template.getType())) return;
        long now = System.currentTimeMillis();
        // compute : la liste ne peut pas être retirée par le nettoyage entre sa lecture et l'ajout
        players.compute(player, (key, current) -> {
            List<BuybackEntry> entries = current != null ? current : new ArrayList<>();
            addTo(entries, template, amount, price, economy, now);
            return entries;
        });
        dirty.set(true);
    }

    private void addTo(List<BuybackEntry> entries, ItemStack template, int amount, double price, String economy, long now) {
        synchronized (entries) {
            BuybackEntry same = null;
            for (BuybackEntry entry : entries) {
                if (entry.economy.equals(economy) && entry.template.isSimilar(template)) {
                    same = entry;
                    break;
                }
            }
            if (same != null) {
                same.amount += amount;
                same.price += price;
                same.soldAt = now;
                same.expiresAt = now + durationMillis;
            } else {
                entries.add(new BuybackEntry(UUID.randomUUID().toString(), template.asOne(), economy, amount, price, now, now + durationMillis));
                // Trop d'entrées : la moins chère part (à prix égal, la plus ancienne)
                while (entries.size() > maxEntries) {
                    entries.remove(entries.stream()
                        .min(Comparator.comparingDouble((BuybackEntry e) -> e.price).thenComparingLong(e -> e.soldAt))
                        .orElseThrow());
                }
            }
        }
    }

    /** Entrées encore valables d'un joueur, de la vente la plus récente à la plus ancienne. */
    public List<BuybackEntry.Snapshot> list(UUID player) {
        List<BuybackEntry> entries = players.get(player);
        if (entries == null) return List.of();
        long now = System.currentTimeMillis();
        List<BuybackEntry.Snapshot> list = new ArrayList<>();
        synchronized (entries) {
            for (BuybackEntry entry : entries) {
                if (entry.expiresAt > now) list.add(entry.snapshot());
            }
        }
        list.sort(Comparator.comparingLong(BuybackEntry.Snapshot::soldAt).reversed().thenComparing(BuybackEntry.Snapshot::id));
        return list;
    }

    /** Lot retiré d'une entrée pour un rachat. */
    public record Taken(String id, ItemStack template, String economy, int count, double cost, long soldAt, long expiresAt) {
    }

    /**
     * Retire {@code count} objets d'une entrée (moins s'il en reste moins) et leur part du prix. Null si l'entrée n'existe
     * plus ou a expiré : un double clic ne rachète jamais deux fois le même lot.
     */
    public Taken take(UUID player, String id, int count) {
        List<BuybackEntry> entries = players.get(player);
        if (entries == null || count <= 0) return null;
        long now = System.currentTimeMillis();
        synchronized (entries) {
            Iterator<BuybackEntry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                BuybackEntry entry = iterator.next();
                if (!entry.id.equals(id)) continue;
                if (entry.expiresAt <= now) return null;
                int taken = Math.min(count, entry.amount);
                double cost = entry.priceOf(taken);
                entry.amount -= taken;
                entry.price = Math.max(0.0, entry.price - cost);
                if (entry.amount <= 0) iterator.remove();
                dirty.set(true);
                return new Taken(entry.id, entry.template, entry.economy, taken, cost, entry.soldAt, entry.expiresAt);
            }
        }
        return null;
    }

    /** Rachat qui n'a pas pu se faire (paiement refusé) : le lot revient à son entrée, tel quel. */
    public void putBack(UUID player, Taken taken) {
        players.compute(player, (key, current) -> {
            List<BuybackEntry> entries = current != null ? current : new ArrayList<>();
            synchronized (entries) {
                BuybackEntry same = null;
                for (BuybackEntry entry : entries) {
                    if (entry.id.equals(taken.id())) {
                        same = entry;
                        break;
                    }
                }
                if (same != null) {
                    same.amount += taken.count();
                    same.price += taken.cost();
                } else {
                    entries.add(new BuybackEntry(taken.id(), taken.template(), taken.economy(), taken.count(), taken.cost(),
                        taken.soldAt(), taken.expiresAt()));
                }
            }
            return entries;
        });
        dirty.set(true);
    }

    /** Entrées expirées retirées (tâche périodique). */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        for (UUID player : new ArrayList<>(players.keySet())) {
            // computeIfPresent : aucun ajout ne peut viser la liste pendant qu'elle est retirée
            players.computeIfPresent(player, (key, entries) -> {
                synchronized (entries) {
                    if (entries.removeIf(entry -> entry.expiresAt <= now)) dirty.set(true);
                    return entries.isEmpty() ? null : entries;
                }
            });
        }
    }

    // =========================================================================
    //  FICHIER
    // =========================================================================

    public void load() {
        players.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        int loaded = 0;
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<BuybackEntry> entries = new ArrayList<>();
            for (String id : section.getKeys(false)) {
                ConfigurationSection row = section.getConfigurationSection(id);
                if (row == null || row.getLong("expires") <= now) continue;
                try {
                    ItemStack template = ItemStack.deserializeBytes(Base64.getDecoder().decode(row.getString("item", "")));
                    entries.add(new BuybackEntry(id, template.asOne(), row.getString("economy", "VAULT"), row.getInt("amount"),
                        row.getDouble("price"), row.getLong("sold"), row.getLong("expires")));
                    loaded++;
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Rachat illisible ignoré (" + key + " / " + id + ")", e);
                }
            }
            if (!entries.isEmpty()) players.put(player, entries);
        }
        plugin.getLogger().info(loaded + " vente(s) rachetable(s) reprise(s) de rachats.yml");
    }

    /** Écrit rachats.yml s'il a changé (hors du thread principal ou à l'arrêt). Fichier temporaire puis remplacement. */
    public void save() {
        // Une sauvegarde à la fois : la dernière commencée écrit toujours l'état le plus récent
        synchronized (fileLock) {
            saveLocked();
        }
    }

    private void saveLocked() {
        if (!dirty.getAndSet(false)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<BuybackEntry>> player : players.entrySet()) {
            List<BuybackEntry.Snapshot> rows = new ArrayList<>();
            synchronized (player.getValue()) {
                for (BuybackEntry entry : player.getValue()) rows.add(entry.snapshot());
            }
            for (BuybackEntry.Snapshot row : rows) {
                String base = player.getKey() + "." + row.id();
                yaml.set(base + ".item", Base64.getEncoder().encodeToString(row.template().serializeAsBytes()));
                yaml.set(base + ".economy", row.economy());
                yaml.set(base + ".amount", row.amount());
                yaml.set(base + ".price", row.price());
                yaml.set(base + ".sold", row.soldAt());
                yaml.set(base + ".expires", row.expiresAt());
            }
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty.set(true);
            plugin.getLogger().log(Level.SEVERE, "rachats.yml non écrit", e);
        }
    }

    public boolean isIgnored(Material material) {
        return ignored.contains(material);
    }
}
