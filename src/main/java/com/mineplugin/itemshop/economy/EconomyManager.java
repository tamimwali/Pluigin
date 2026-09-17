package com.mineplugin.itemshop.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Manages the Vault economy connection and wraps financial operations safely.
 */
public final class EconomyManager {

    private final JavaPlugin plugin;
    private Economy vaultEconomy;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to find a registered Economy service provider via Vault.
     *
     * @return true if Vault and an economy implementation were successfully located.
     */
    public boolean hookEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        this.vaultEconomy = rsp.getProvider();
        plugin.getLogger().info("Successfully hooked into Vault Economy provider: " + vaultEconomy.getName());
        return true;
    }

    public boolean isHooked() {
        return vaultEconomy != null;
    }

    /**
     * Checks if the player has at least the required amount.
     */
    public boolean hasBalance(Player player, double amount) {
        if (!isHooked()) return false;
        return vaultEconomy.has(player, amount);
    }

    /**
     * Gets the current balance of the player.
     */
    public double getBalance(Player player) {
        if (!isHooked()) return 0.0;
        return vaultEconomy.getBalance(player);
    }

    /**
     * Withdraws money from the player.
     *
     * @return true if the withdrawal transaction succeeded.
     */
    public boolean withdraw(Player player, double amount) {
        if (!isHooked() || amount <= 0) return false;
        EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().log(Level.WARNING, String.format(
                    "Withdrawal failed for player %s of amount %.2f: %s",
                    player.getName(), amount, response.errorMessage));
            return false;
        }
        return true;
    }

    /**
     * Deposits money into the player's account.
     *
     * @return true if the deposit transaction succeeded.
     */
    public boolean deposit(Player player, double amount) {
        if (!isHooked() || amount <= 0) return false;
        EconomyResponse response = vaultEconomy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().log(Level.WARNING, String.format(
                    "Deposit failed for player %s of amount %.2f: %s",
                    player.getName(), amount, response.errorMessage));
            return false;
        }
        return true;
    }

    /**
     * Formats currency using Vault's currency format (e.g., "$150.00" or "150 Coins").
     */
    public String format(double amount) {
        if (!isHooked()) {
            return String.format("$%.2f", amount);
        }
        return vaultEconomy.format(amount);
    }
}