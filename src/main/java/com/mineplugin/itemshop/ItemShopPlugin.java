package com.mineplugin.itemshop;

import com.mineplugin.itemshop.commands.BuyCommand;
import com.mineplugin.itemshop.commands.PriceCommand;
import com.mineplugin.itemshop.commands.SellCommand;
import com.mineplugin.itemshop.commands.ShopAdminCommand;
import com.mineplugin.itemshop.economy.EconomyManager;
import com.mineplugin.itemshop.price.PriceManager;
import com.mineplugin.itemshop.util.MessageUtils;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Modern, production-ready PaperMC Item Shop plugin.
 * Handles Vault economy transactions, configurable item pricing,
 * anti-dupe inventory safety, and Adventure MiniMessage chat formatting.
 */
public final class ItemShopPlugin extends JavaPlugin {

    private static ItemShopPlugin instance;
    private EconomyManager economyManager;
    private PriceManager priceManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Save default config and prices files if missing
        saveDefaultConfig();
        saveResource("prices.yml", false);

        // 2. Initialize MessageUtils with active config prefix
        MessageUtils.init(this);

        // 3. Hook into Vault Economy
        this.economyManager = new EconomyManager(this);
        if (!this.economyManager.hookEconomy()) {
            getLogger().log(Level.SEVERE, "==========================================================");
            getLogger().log(Level.SEVERE, "[ItemShop] Vault or an Economy provider was NOT detected!");
            getLogger().log(Level.SEVERE, "[ItemShop] Please install Vault and an economy plugin (e.g. EssentialsX).");
            getLogger().log(Level.SEVERE, "[ItemShop] Disabling ItemShop safely to prevent economy corruption...");
            getLogger().log(Level.SEVERE, "==========================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Load price database
        this.priceManager = new PriceManager(this);
        this.priceManager.loadPrices();

        // 5. Register player and admin commands
        registerCommands();

        getLogger().info(String.format("ItemShop v%s successfully enabled with %d configured item prices.",
                getPluginMeta().getVersion(),
                priceManager.getLoadedPricesCount()));
    }

    @Override
    public void onDisable() {
        if (priceManager != null) {
            priceManager.savePrices();
        }
        getLogger().info("ItemShop has been disabled gracefully.");
    }

    private void registerCommands() {
        // /buy <item> [amount]
        BuyCommand buyCmd = new BuyCommand(this);
        registerCommand("buy", buyCmd);

        // /sell <item|hand|all> [amount]
        SellCommand sellCmd = new SellCommand(this);
        registerCommand("sell", sellCmd);

        // /price [item|hand]
        PriceCommand priceCmd = new PriceCommand(this);
        registerCommand("price", priceCmd);

        // /shop <setbuy|setsell|reload>
        ShopAdminCommand shopCmd = new ShopAdminCommand(this);
        registerCommand("shop", shopCmd);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                command.setTabCompleter(tabCompleter);
            }
        } else {
            getLogger().warning("Could not register command /" + name + " (missing in plugin.yml descriptor).");
        }
    }

    public static ItemShopPlugin getInstance() {
        return Objects.requireNonNull(instance, "ItemShopPlugin instance is null!");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PriceManager getPriceManager() {
        return priceManager;
    }
}