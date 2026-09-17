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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles /buy <item> [amount] command with robust anti-dupe, space verification,
 * and Vault transaction safety.
 */
public final class BuyCommand implements CommandExecutor, TabCompleter {

    private final ItemShopPlugin plugin;

    public BuyCommand(ItemShopPlugin plugin) {
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

        if (!player.hasPermission("itemshop.buy")) {
            MessageUtils.sendMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length < 1) {
            MessageUtils.sendRaw(player, "<yellow>Usage: <white>/" + label + " <item_name> [amount]</white></yellow>");
            return true;
        }

        // 1. Resolve Material
        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem() || material.isAir()) {
            MessageUtils.sendMessage(player, "messages.item-not-found", "item", args[0]);
            return true;
        }

        // 2. Parse Amount
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    MessageUtils.sendRaw(player, "<red>Amount must be a positive integer greater than 0.</red>");
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtils.sendRaw(player, "<red>Invalid amount specified: <yellow>" + args[1] + "</yellow></red>");
                return true;
            }
        }

        int maxBuy = plugin.getConfig().getInt("settings.max-buy-amount", 2304);
        if (amount > maxBuy) {
            MessageUtils.sendRaw(player, "<red>You can only purchase up to <yellow>" + maxBuy + "</yellow> items at once.</red>");
            return true;
        }

        PriceManager priceManager = plugin.getPriceManager();
        Optional<ItemPrice> priceOpt = priceManager.getPrice(material);

        if (priceOpt.isEmpty() || !priceOpt.get().canBuy()) {
            MessageUtils.sendMessage(player, "messages.buy-disabled", "item", material.name());
            return true;
        }

        double unitPrice = priceOpt.get().buyPrice();
        double totalCost = unitPrice * amount;
        EconomyManager economyManager = plugin.getEconomyManager();

        // 3. Balance verification
        if (!economyManager.hasBalance(player, totalCost)) {
            MessageUtils.sendMessage(player, "messages.buy-insufficient-funds",
                    "cost", economyManager.format(totalCost),
                    "balance", economyManager.format(economyManager.getBalance(player)));
            return true;
        }

        // 4. Inventory capacity calculation BEFORE taking any money (Anti-Overflow / Anti-Dupe)
        int spaceAvailable = calculateInventoryCapacity(player, material);
        if (spaceAvailable < amount) {
            int maxStack = material.getMaxStackSize();
            int slotsNeeded = (int) Math.ceil((double) (amount - spaceAvailable) / maxStack);
            MessageUtils.sendMessage(player, "messages.buy-inventory-full",
                    "slots_needed", String.valueOf(slotsNeeded));
            return true;
        }

        // 5. Deduct money
        if (!economyManager.withdraw(player, totalCost)) {
            MessageUtils.sendMessage(player, "messages.economy-error");
            return true;
        }

        // 6. Give items to player safely
        giveItems(player, material, amount);

        // 7. Feedback & audio
        if (plugin.getConfig().getBoolean("settings.play-sounds", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        }

        MessageUtils.sendMessage(player, "messages.buy-success",
                "amount", String.valueOf(amount),
                "item", material.name(),
                "cost", economyManager.format(totalCost),
                "balance", economyManager.format(economyManager.getBalance(player)));

        return true;
    }

    /**
     * Calculates maximum amount of this material the player can store without dropping.
     */
    private int calculateInventoryCapacity(Player player, Material material) {
        int capacity = 0;
        int maxStack = material.getMaxStackSize();
        ItemStack[] storage = player.getInventory().getStorageContents();

        for (ItemStack stack : storage) {
            if (stack == null || stack.getType().isAir()) {
                capacity += maxStack;
            } else if (stack.isSimilar(new ItemStack(material))) {
                capacity += Math.max(0, maxStack - stack.getAmount());
            }
        }
        return capacity;
    }

    private void giveItems(Player player, Material material, int amount) {
        int maxStack = material.getMaxStackSize();
        int remaining = amount;

        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(material, stackAmount);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                // Failsafe: drop directly at player location if unexpected overflow
                for (ItemStack dropped : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
            }
            remaining -= stackAmount;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String query = args[0].toUpperCase(Locale.ROOT);
            return plugin.getPriceManager().getConfiguredMaterials().stream()
                    .filter(plugin.getPriceManager()::canBuy)
                    .map(Material::name)
                    .filter(name -> name.startsWith(query))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return List.of("1", "16", "32", "64");
        }

        return Collections.emptyList();
    }
}