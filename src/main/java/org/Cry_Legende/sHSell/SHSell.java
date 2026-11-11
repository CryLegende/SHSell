package org.Cry_Legende.sHSell;

import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.Cry_Legende.sHSell.buyback.BuybackMenus;
import org.Cry_Legende.sHSell.buyback.BuybackStore;
import org.Cry_Legende.sHSell.buyback.SaleCapture;
import org.Cry_Legende.sHSell.pages.PagesLink;
import org.Cry_Legende.sHSell.pages.ShInventoryPagesLink;
import org.Cry_Legende.sHSell.shop.Economies;
import org.Cry_Legende.sHSell.shop.SellAllPages;
import org.Cry_Legende.sHSell.shop.SellCommandHook;
import org.Cry_Legende.sHSell.shop.ShopBridge;
import org.Cry_Legende.sHSell.util.ConfigFiles;
import org.Cry_Legende.sHSell.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Compléments de vente pour ShopGUIPlus : /sell allpages (vend l'inventaire porté et toutes les pages d'inventaire
 * de SHInventoryPages) et menu de rachat des ventes récentes (/rachat).
 */
public final class SHSell extends JavaPlugin implements Listener {

    private static final String RELOAD_PERMISSION = "shsell.admin.reload";
    private static final String VIEW_PERMISSION = "shsell.admin.view";

    private final Texts texts = new Texts(this);
    private BuybackStore store;
    private BuybackMenus menus;
    private SellAllPages sellAllPages;
    private SellCommandHook sellHook;
    private ShopBridge shopBridge;
    private PagesLink pages = PagesLink.NONE;
    private Sound buybackSound;
    private DecimalFormat priceFormat;
    private DecimalFormat amountFormat;
    private BukkitTask hookRetry;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        texts.load();
        store = new BuybackStore(this);
        loadSettings();
        store.load();
        menus = new BuybackMenus(this, store);
        hookPages();

        getServer().getPluginManager().registerEvents(new SaleCapture(store), this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();

        try {
            shopBridge = ShopBridge.open();
            sellAllPages = new SellAllPages(this, shopBridge);
            sellHook = new SellCommandHook(this, sellAllPages);
            installSellHook();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "ShopGUIPlus a changé : /sell allpages est désactivé (le rachat reste actif)", e);
        }

        // Expiration et sauvegarde hors du thread principal ; comptes à rebours des menus sur le thread principal
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, store::purgeExpired, 20L * 20, 20L * 20);
        scheduleSave();
        Bukkit.getScheduler().runTaskTimer(this, menus::refreshOpen, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (hookRetry != null) hookRetry.cancel();
        if (saveTask != null) saveTask.cancel();
        if (sellHook != null) sellHook.uninstall();
        if (menus != null) menus.closeAll();
        if (store != null) store.save();
    }

    /** Sauvegarde de rachats.yml à l'intervalle de buyback.save-seconds (relancée par un reload). */
    private void scheduleSave() {
        if (saveTask != null) saveTask.cancel();
        long save = 20L * Math.max(5, getConfig().getInt("buyback.save-seconds", 30));
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, store::save, save, save);
    }

    private void loadSettings() {
        store.reloadSettings();
        buybackSound = sound(getConfig().getString("buyback.sound", "minecraft:entity.experience_orb.pickup"));
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(getConfig().getString("number-locale", "en")));
        priceFormat = new DecimalFormat(getConfig().getString("price-format", "#,##0.##"), symbols);
        amountFormat = new DecimalFormat("#,##0", symbols);
    }

    /** /sell allpages et le rachat vers les pages ne passent par SHInventoryPages que s'il est installé. */
    private void hookPages() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SHInventoryPages")) return;
        try {
            pages = ShInventoryPagesLink.open();
            getLogger().info("SHInventoryPages détecté : /sell allpages vend toutes les pages, les rachats peuvent aller dans les autres pages.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.WARNING, "SHInventoryPages présent mais inutilisable : inventaire porté seulement", e);
        }
    }

    /**
     * ShopGUIPlus pose l'exécuteur de /sell un tick après son démarrage : le branchement est essayé à chaque seconde
     * jusqu'à ce qu'il tienne (une minute au plus), et refait quand ShopGUIPlus signale la fin de son démarrage.
     */
    private void installSellHook() {
        if (sellHook == null) return;
        if (hookRetry != null) hookRetry.cancel();
        int[] attempts = {0};
        hookRetry = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (sellHook.install()) {
                hookRetry.cancel();
                getLogger().info("/sell allpages branché sur la commande /sell de ShopGUIPlus.");
            } else if (++attempts[0] >= 60) {
                hookRetry.cancel();
                getLogger().severe("Commande /sell de ShopGUIPlus introuvable : /sell allpages indisponible.");
            }
        }, 1L, 20L);
    }

    @EventHandler
    public void onShopEnabled(ShopGUIPlusPostEnableEvent event) {
        installSellHook();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (sellAllPages != null) sellAllPages.forget(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    //  COMMANDES
    // =========================================================================

    private void registerCommands() {
        PluginCommand buyback = getCommand("rachat");
        if (buyback != null) {
            buyback.setExecutor(this::onBuybackCommand);
            buyback.setTabCompleter(this::onBuybackTab);
        }
        PluginCommand admin = getCommand("shsell");
        if (admin != null) {
            admin.setExecutor(this::onAdminCommand);
            admin.setTabCompleter((sender, command, alias, args) ->
                args.length == 1 && sender.hasPermission(RELOAD_PERMISSION) && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("reload") : List.of());
        }
    }

    private boolean onBuybackCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            texts.send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            menus.open(player, player.getUniqueId(), player.getName(), false, 0);
            return true;
        }
        if (!player.hasPermission(VIEW_PERMISSION)) {
            texts.send(player, "no-permission");
            return true;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        // Jamais de recherche chez Mojang depuis le thread principal : seuls les joueurs déjà venus sont connus
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            texts.send(player, "player-unknown", "%player%", args[0]);
            return true;
        }
        String name = target.getName() != null ? target.getName() : args[0];
        menus.open(player, target.getUniqueId(), name, !target.getUniqueId().equals(player.getUniqueId()), 0);
        return true;
    }

    private List<String> onBuybackTab(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(VIEW_PERMISSION)) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
            .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .toList();
    }

    private boolean onAdminCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            texts.send(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadFiles(sender);
            return true;
        }
        texts.send(sender, "admin-usage", "%subcommands%", "reload");
        return true;
    }

    /**
     * Relit config.yml et messages.yml. Les deux fichiers sont lus avant tout remplacement : si l'un est illisible,
     * l'ancienne configuration reste active. rachats.yml n'est pas relu.
     */
    private void reloadFiles(CommandSender sender) {
        YamlConfiguration loadedMessages;
        try {
            ConfigFiles.read(this, "config.yml");
            loadedMessages = ConfigFiles.read(this, "messages.yml");
        } catch (ConfigFiles.ReadError e) {
            getLogger().severe("Reload annulé, " + e.file() + " illisible : " + e.detail());
            texts.send(sender, "reload-error", "%file%", e.file(), "%error%", e.detail());
            return;
        }
        reloadConfig();
        texts.apply(loadedMessages);
        loadSettings();
        scheduleSave();
        texts.send(sender, "reloaded", "%ignored%", String.valueOf(store.ignoredCount()));
    }

    // =========================================================================
    //  OUTILS COMMUNS
    // =========================================================================

    public Texts texts() {
        return texts;
    }

    public PagesLink pages() {
        return pages;
    }

    public Sound buybackSound() {
        return buybackSound;
    }

    /** Faux dans un monde ou un mode de jeu où ShopGUIPlus ferme ses boutiques. */
    public boolean canSellHere(Player player) {
        ShopBridge bridge = shopBridge;
        return bridge == null || (!bridge.isGameModeDisabled(player.getGameMode()) && !bridge.isWorldDisabled(player.getWorld().getName()));
    }

    /** Monnaie d'une boutique ShopGUIPlus (clé de {@link Economies}), ou null si elle n'existe plus. */
    public EconomyProvider economy(String key) {
        return Economies.provider(key);
    }

    /** Somme suivie du nom de sa monnaie (messages.yml : currency-<MONNAIE>). */
    public String formatPrice(String economy, double value) {
        return texts.raw("price", "%amount%", priceFormat.format(value), "%currency%", texts.raw("currency-" + economy));
    }

    public String formatAmount(long value) {
        return amountFormat.format(value);
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup) ; "" = aucun. */
    private static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }
}
