package org.Cry_Legende.sHSell.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Messages de messages.yml (codes & et &#RRGGBB), envoyés en composants. */
public final class Texts {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
        .character('&').hexCharacter('#').hexColors().build();

    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public Texts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** messages.yml du dossier du plugin, complété par celui du jar pour les textes qui y manquent. */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "messages.yml du jar illisible", e);
        }
    }

    /** Texte brut (codes &) avec ses remplacements, par paires : raw("cle", "%a%", "1"). */
    public String raw(String key, String... replacements) {
        String text = messages.getString(key);
        if (text == null) {
            plugin.getLogger().warning("Message manquant dans messages.yml : " + key);
            return key;
        }
        return replace(text, replacements);
    }

    public List<String> rawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>();
        for (String line : messages.getStringList(key)) lines.add(replace(line, replacements));
        return lines;
    }

    public Component get(String key, String... replacements) {
        return AMPERSAND.deserialize(raw(key, replacements));
    }

    /** Envoie un message ; un message vide dans messages.yml n'est pas envoyé. */
    public void send(CommandSender target, String key, String... replacements) {
        String text = raw(key, replacements);
        if (target != null && !text.isEmpty()) target.sendMessage(AMPERSAND.deserialize(text));
    }

    /**
     * Envoie un message dont {@code %item%} est le nom de l'objet : nom personnalisé, sinon nom traduit par le jeu du
     * joueur (français pour un client en français).
     */
    public void sendWithItem(CommandSender target, String key, ItemStack item, String... replacements) {
        String text = raw(key, replacements);
        if (target == null || text.isEmpty()) return;
        target.sendMessage(AMPERSAND.deserialize(text).replaceText(builder -> builder.matchLiteral("%item%").replacement(item.effectiveName())));
    }

    /** Nom ou ligne de description d'objet : jamais en italique. */
    public static Component item(String raw) {
        return AMPERSAND.deserialize(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> items(List<String> raw) {
        List<Component> lines = new ArrayList<>(raw.size());
        for (String line : raw) lines.add(item(line));
        return lines;
    }

    /** Nom affiché d'un objet, sans couleurs : nom personnalisé, sinon nom traduit côté serveur. */
    public static String plainName(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            if (replacements[i] != null && replacements[i + 1] != null) text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }
}
