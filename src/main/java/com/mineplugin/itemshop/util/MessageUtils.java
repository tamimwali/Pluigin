package com.mineplugin.itemshop.util;

import com.mineplugin.itemshop.ItemShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Utility for parsing Adventure MiniMessage syntax and formatting messages.
 */
public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static String prefix = "<gradient:#38bdf8:#818cf8><b>[ItemShop]</b></gradient> ";

    private MessageUtils() {}

    public static void init(ItemShopPlugin plugin) {
        prefix = plugin.getConfig().getString("prefix", "<gradient:#38bdf8:#818cf8><b>[ItemShop]</b></gradient> ");
    }

    public static Component parse(String rawMessage) {
        return MINI_MESSAGE.deserialize(rawMessage);
    }

    public static void sendRaw(Player player, String message) {
        player.sendMessage(MINI_MESSAGE.deserialize(prefix + message));
    }

    /**
     * Retrieves message path from config.yml, replaces key-value placeholders,
     * parses MiniMessage tags, and sends to player.
     */
    public static void sendMessage(Player player, String configPath, String... placeholders) {
        ItemShopPlugin plugin = ItemShopPlugin.getInstance();
        String message = plugin.getConfig().getString(configPath, "<red>Missing string: " + configPath + "</red>");

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = placeholders[i];
            String val = Objects.toString(placeholders[i + 1], "");
            message = message.replace("<" + key + ">", val);
        }

        player.sendMessage(MINI_MESSAGE.deserialize(prefix + message));
    }
}