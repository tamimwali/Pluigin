package com.mineplugin.itemshop.commands;

import com.mineplugin.itemshop.ItemShopPlugin;
import com.mineplugin.itemshop.economy.EconomyManager;
import com.mineplugin.itemshop.price.ItemPrice;
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
 * Handles /price [item_name|hand] command. Displays single & stack price info.
 */
public final class PriceCommand implements CommandExecutor, TabCompleter {

    private final ItemShopPlugin plugin;

    public PriceCommand(ItemShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("itemshop.price")) {
            if (sender instanceof Player player) {
                MessageUtils.sendMessage(player, "messages.no-permission");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>No permission.</red>"));
            }
            return true;
        }

        Material material;

        if (args.length == 0 || args[0].equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtils.parse("<red>Console must specify an item name: /price <item></red>"));
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                MessageUtils.sendRaw(player, "<red>You must hold an item or specify an item name: <yellow>/price <item></yellow></red>");
                return true;
            }
            material = hand.getType();
        } else {
            material = Material.matchMaterial(args[0]);
            if (material == null || !material.isItem() || material.isAir()) {
                if (sender instanceof Player player) {
                    MessageUtils.sendMessage(player, "messages.item-not-found", "item", args[0]);
                } else {
                    sender.sendMessage(MessageUtils.parse("<red>Unknown item: " + args[0] + "</red>"));
                }
                return true;
            }
        }

        displayPrice(sender, material);
        return true;
    }

    private void displayPrice(CommandSender sender, Material material) {
        PriceManager priceManager = plugin.getPriceManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        Optional<ItemPrice> priceOpt = priceManager.getPrice(material);

        String itemName = material.name();
        sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-header",
                "<gradient:#38bdf8:#818cf8><b>=========== Item Price: <item> ===========</b></gradient>")
                .replace("<item>", itemName)));

        if (priceOpt.isPresent()) {
            ItemPrice price = priceOpt.get();

            // Buy Price
            if (price.canBuy()) {
                double buyOne = price.buyPrice();
                double buyStack = buyOne * 64;
                sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-buy")
                        .replace("<buy_one>", economyManager.format(buyOne))
                        .replace("<buy_stack>", economyManager.format(buyStack))));
            } else {
                sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-buy-disabled")));
            }

            // Sell Price
            if (price.canSell()) {
                double sellOne = price.sellPrice();
                double sellStack = sellOne * 64;
                sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-sell")
                        .replace("<sell_one>", economyManager.format(sellOne))
                        .replace("<sell_stack>", economyManager.format(sellStack))));
            } else {
                sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-sell-disabled")));
            }
        } else {
            sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-buy-disabled")));
            sender.sendMessage(MessageUtils.parse(plugin.getConfig().getString("messages.price-sell-disabled")));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String query = args[0].toUpperCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            suggestions.add("hand");
            plugin.getPriceManager().getConfiguredMaterials().stream()
                    .map(Material::name)
                    .forEach(suggestions::add);

            return suggestions.stream()
                    .filter(s -> s.toUpperCase(Locale.ROOT).startsWith(query))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}