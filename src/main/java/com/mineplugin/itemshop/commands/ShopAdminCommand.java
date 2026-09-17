package com.mineplugin.itemshop.commands;

import com.mineplugin.itemshop.ItemShopPlugin;
import com.mineplugin.itemshop.economy.EconomyManager;
import com.mineplugin.itemshop.price.PriceManager;
import com.mineplugin.itemshop.util.MessageUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin management command:
 * /shop setbuy <price>   - sets buy price for held item
 * /shop setsell <price>  - sets sell price for held item
 * /shop reload           - reloads config.yml and prices.yml
 */
public final class ShopAdminCommand implements CommandExecutor, TabCompleter {

    private final ItemShopPlugin plugin;

    public ShopAdminCommand(ItemShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("itemshop.admin")) {
            if (sender instanceof Player player) {
                MessageUtils.sendMessage(player, "messages.no-permission");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You do not have permission to execute this command.</red>"));
            }
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                plugin.reloadConfig();
                MessageUtils.init(plugin);
                plugin.getPriceManager().loadPrices();
                int count = plugin.getPriceManager().getLoadedPricesCount();
                if (sender instanceof Player player) {
                    MessageUtils.sendMessage(player, "messages.admin-reload-success", "count", String.valueOf(count));
                } else {
                    sender.sendMessage(MessageUtils.parse("<green>ItemShop configs and prices reloaded (" + count + " items indexed).</green>"));
                }
                return true;
            }

            case "setbuy" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtils.parse("<red>This command requires holding an item in hand (player only).</red>"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtils.sendRaw(player, "<yellow>Usage: <white>/" + label + " setbuy <price></white> (Set -1 to disable buying)</yellow>");
                    return true;
                }
                return handleSetPrice(player, args[1], true);
            }

            case "setsell" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtils.parse("<red>This command requires holding an item in hand (player only).</red>"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtils.sendRaw(player, "<yellow>Usage: <white>/" + label + " setsell <price></white> (Set -1 to disable selling)</yellow>");
                    return true;
                }
                return handleSetPrice(player, args[1], false);
            }

            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private boolean handleSetPrice(Player player, String rawPrice, boolean isBuy) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            MessageUtils.sendRaw(player, "<red>You must hold the item you wish to price in your main hand.</red>");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(rawPrice);
        } catch (NumberFormatException e) {
            MessageUtils.sendRaw(player, "<red>Invalid price number: <yellow>" + rawPrice + "</yellow></red>");
            return true;
        }

        Material material = hand.getType();
        PriceManager priceManager = plugin.getPriceManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        if (isBuy) {
            priceManager.setBuyPrice(material, price);
            MessageUtils.sendMessage(player, "messages.admin-setbuy-success",
                    "item", material.name(),
                    "price", price > 0 ? economyManager.format(price) : "DISABLED (-1)");
        } else {
            priceManager.setSellPrice(material, price);
            MessageUtils.sendMessage(player, "messages.admin-setsell-success",
                    "item", material.name(),
                    "price", price > 0 ? economyManager.format(price) : "DISABLED (-1)");
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageUtils.parse("<gradient:#38bdf8:#818cf8><b>=== ItemShop Admin Commands ===</b></gradient>"));
        sender.sendMessage(MessageUtils.parse("<yellow>/" + label + " setbuy <price></yellow> <gray>- Set buy price for held item</gray>"));
        sender.sendMessage(MessageUtils.parse("<yellow>/" + label + " setsell <price></yellow> <gray>- Set sell price for held item</gray>"));
        sender.sendMessage(MessageUtils.parse("<yellow>/" + label + " reload</yellow> <gray>- Reload configs and prices.yml</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("itemshop.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return List.of("setbuy", "setsell", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("setbuy") || args[0].equalsIgnoreCase("setsell"))) {
            return List.of("10.0", "50.0", "100.0", "-1");
        }

        return Collections.emptyList();
    }
}