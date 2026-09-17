package com.mineplugin.itemshop.commands;

import com.mineplugin.itemshop.ItemShopPlugin;
import com.mineplugin.itemshop.economy.EconomyManager;
import com.mineplugin.itemshop.price.ItemPrice;
import com.mineplugin.itemshop.price.PriceManager;
import com.mineplugin.itemshop.util.MessageUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles /sell <item|hand|all> [amount] with exact stack reduction and Vault deposit.
 */
public final class SellCommand implements CommandExecutor, TabCompleter {

    private final ItemShopPlugin plugin;

    public SellCommand(ItemShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.parse("<red>This command can only be executed by players.</red>"));
            return true;
        }

        if (!player.hasPermission("itemshop.sell")) {
            MessageUtils.sendMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length < 1) {
            MessageUtils.sendRaw(player, "<yellow>Usage: <white>/" + label + " <item_name|hand|all> [amount]</white></yellow>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Branch 1: /sell hand [amount]
        if (sub.equals("hand")) {
            return handleSellHand(player, args);
        }

        // Branch 2: /sell all
        if (sub.equals("all")) {
            return handleSellAll(player);
        }

        // Branch 3: /sell <item_name> [amount]
        return handleSellItem(player, args[0], args.length >= 2 ? args[1] : "all");
    }

    private boolean handleSellHand(Player player, String[] args) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            MessageUtils.sendMessage(player, "messages.sell-empty-hand");
            return true;
        }

        Material material = hand.getType();
        PriceManager priceManager = plugin.getPriceManager();
        if (!priceManager.canSell(material)) {
            MessageUtils.sendMessage(player, "messages.sell-disabled", "item", material.name());
            return true;
        }

        int maxToSell = hand.getAmount();
        if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
            try {
                int requested = Integer.parseInt(args[1]);
                if (requested <= 0) {
                    MessageUtils.sendRaw(player, "<red>Amount must be positive.</red>");
                    return true;
                }
                maxToSell = Math.min(requested, maxToSell);
            } catch (NumberFormatException e) {
                MessageUtils.sendRaw(player, "<red>Invalid amount specified: <yellow>" + args[1] + "</yellow></red>");
                return true;
            }
        }

        double unitPrice = priceManager.getSellPrice(material);
        double totalEarned = unitPrice * maxToSell;

        // Safely shrink hand stack
        hand.subtract(maxToSell);
        player.getInventory().setItemInMainHand(hand);

        EconomyManager economyManager = plugin.getEconomyManager();
        economyManager.deposit(player, totalEarned);

        playCashSound(player);
        MessageUtils.sendMessage(player, "messages.sell-success",
                "amount", String.valueOf(maxToSell),
                "item", material.name(),
                "earned", economyManager.format(totalEarned),
                "balance", economyManager.format(economyManager.getBalance(player)));

        return true;
    }

    private boolean handleSellAll(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        PriceManager priceManager = plugin.getPriceManager();

        Map<Material, Integer> itemsToSell = new HashMap<>();
        double grandTotal = 0.0;
        int totalItemCount = 0;

        // Scan inventory for sellable items
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;
            Material mat = item.getType();
            if (priceManager.canSell(mat)) {
                itemsToSell.put(mat, itemsToSell.getOrDefault(mat, 0) + item.getAmount());
            }
        }

        if (itemsToSell.isEmpty()) {
            MessageUtils.sendMessage(player, "messages.sell-all-none");
            return true;
        }

        // Calculate total and remove matching items
        for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();
            double price = priceManager.getSellPrice(mat);
            grandTotal += price * amount;
            totalItemCount += amount;
            inv.remove(mat);
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        economyManager.deposit(player, grandTotal);

        playCashSound(player);
        MessageUtils.sendMessage(player, "messages.sell-all-summary",
                "total_items", String.valueOf(totalItemCount),
                "types_count", String.valueOf(itemsToSell.size()),
                "total_earned", economyManager.format(grandTotal));

        return true;
    }

    private boolean handleSellItem(Player player, String rawItem, String rawAmount) {
        Material material = Material.matchMaterial(rawItem);
        if (material == null || !material.isItem() || material.isAir()) {
            MessageUtils.sendMessage(player, "messages.item-not-found", "item", rawItem);
            return true;
        }

        PriceManager priceManager = plugin.getPriceManager();
        if (!priceManager.canSell(material)) {
            MessageUtils.sendMessage(player, "messages.sell-disabled", "item", material.name());
            return true;
        }

        PlayerInventory inv = player.getInventory();
        int availableCount = countMaterialInInventory(inv, material);

        if (availableCount <= 0) {
            MessageUtils.sendMessage(player, "messages.sell-no-items", "item", material.name());
            return true;
        }

        int toSell = availableCount;
        if (!rawAmount.equalsIgnoreCase("all")) {
            try {
                int requested = Integer.parseInt(rawAmount);
                if (requested <= 0) {
                    MessageUtils.sendRaw(player, "<red>Amount must be positive.</red>");
                    return true;
                }
                toSell = Math.min(requested, availableCount);
            } catch (NumberFormatException e) {
                MessageUtils.sendRaw(player, "<red>Invalid amount specified: <yellow>" + rawAmount + "</yellow></red>");
                return true;
            }
        }

        // Remove exact count from inventory
        removeExactAmount(inv, material, toSell);

        double unitPrice = priceManager.getSellPrice(material);
        double totalEarned = unitPrice * toSell;

        EconomyManager economyManager = plugin.getEconomyManager();
        economyManager.deposit(player, totalEarned);

        playCashSound(player);
        MessageUtils.sendMessage(player, "messages.sell-success",
                "amount", String.valueOf(toSell),
                "item", material.name(),
                "earned", economyManager.format(totalEarned),
                "balance", economyManager.format(economyManager.getBalance(player)));

        return true;
    }

    private int countMaterialInInventory(PlayerInventory inv, Material material) {
        int count = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeExactAmount(PlayerInventory inv, Material material, int amountToRemove) {
        int remaining = amountToRemove;
        ItemStack[] contents = inv.getStorageContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;

            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                inv.setItem(i, null);
            } else {
                stack.subtract(remaining);
                remaining = 0;
                break;
            }

            if (remaining <= 0) break;
        }
    }

    private void playCashSound(Player player) {
        if (plugin.getConfig().getBoolean("settings.play-sounds", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String query = args[0].toUpperCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            list.add("hand");
            list.add("all");
            plugin.getPriceManager().getConfiguredMaterials().stream()
                    .filter(plugin.getPriceManager()::canSell)
                    .map(Material::name)
                    .forEach(list::add);

            return list.stream()
                    .filter(s -> s.toUpperCase(Locale.ROOT).startsWith(query))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return List.of("all", "1", "16", "32", "64");
        }

        return Collections.emptyList();
    }
}