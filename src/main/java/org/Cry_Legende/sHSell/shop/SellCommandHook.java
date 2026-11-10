package org.Cry_Legende.sHSell.shop;

import org.Cry_Legende.sHSell.SHSell;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ajoute « allpages » à la commande /sell de ShopGUIPlus (et à ses alias) : SHSell se place devant son exécuteur et
 * lui laisse tout le reste. Passer par l'exécuteur plutôt que par le texte tapé couvre aussi les alias de commands.yml
 * et les commandes lancées par d'autres plugins. ShopGUIPlus pose son exécuteur un tick après son démarrage : le
 * branchement est donc refait après, et défait à l'arrêt de SHSell.
 */
public final class SellCommandHook implements CommandExecutor, TabCompleter {

    /** Sous-commandes de /sell, avec la permission qui les ouvre. */
    private static final String[][] SUBCOMMANDS = {
        {"hand", "shopguiplus.sell.hand"},
        {"handall", "shopguiplus.sell.hand.all"},
        {"all", "shopguiplus.sell.all"},
        {"allpages", "shsell.allpages"},
    };

    private final SHSell plugin;
    private final SellAllPages sellAllPages;
    private PluginCommand command;
    private CommandExecutor original;
    private TabCompleter originalCompleter;

    public SellCommandHook(SHSell plugin, SellAllPages sellAllPages) {
        this.plugin = plugin;
        this.sellAllPages = sellAllPages;
    }

    /** Se place devant l'exécuteur de /sell s'il ne l'est pas déjà. Vrai si le branchement est en place. */
    public boolean install() {
        Plugin shop = Bukkit.getPluginManager().getPlugin("ShopGUIPlus");
        PluginCommand sell = shop == null ? null : Bukkit.getPluginCommand(shop.getName().toLowerCase(Locale.ROOT) + ":sell");
        if (sell == null) return false;
        CommandExecutor current = sell.getExecutor();
        if (current == this) return true;
        // Sans exécuteur posé, Bukkit renvoie le plugin lui-même : ShopGUIPlus n'a pas encore fini de démarrer
        if (current == shop) return false;
        command = sell;
        original = current;
        originalCompleter = sell.getTabCompleter();
        sell.setExecutor(this);
        sell.setTabCompleter(this);
        return true;
    }

    public void uninstall() {
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(original);
            command.setTabCompleter(originalCompleter);
        }
        command = null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("allpages")) {
            if (sender instanceof Player player) {
                sellAllPages.execute(player);
            } else {
                plugin.texts().send(sender, "player-only");
            }
            return true;
        }
        return original.onCommand(sender, cmd, label, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return originalCompleter != null ? originalCompleter.onTabComplete(sender, cmd, alias, args) : List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (String[] sub : SUBCOMMANDS) {
            if (sub[0].startsWith(prefix) && sender.hasPermission(sub[1])) names.add(sub[0]);
        }
        return names;
    }
}
