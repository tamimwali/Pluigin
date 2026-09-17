package com.mineplugin.itemshop.price;

import com.mineplugin.itemshop.ItemShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages item prices loaded from prices.yml with in-memory caching
 * and disk synchronization.
 */
public final class PriceManager {

    private final ItemShopPlugin plugin;
    private final File pricesFile;
    private FileConfiguration pricesConfig;
    private final Map<Material, ItemPrice> priceMap = new ConcurrentHashMap<>();

    public PriceManager(ItemShopPlugin plugin) {
        this.plugin = plugin;
        this.pricesFile = new File(plugin.getDataFolder(), "prices.yml");
    }

    /**
     * Loads or reloads all prices from prices.yml.
     */
    public synchronized void loadPrices() {
        if (!pricesFile.exists()) {
            plugin.saveResource("prices.yml", false);
        }

        this.pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);
        this.priceMap.clear();

        for (String key : pricesConfig.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Skipping unknown material in prices.yml: " + key);
                continue;
            }

            ConfigurationSection section = pricesConfig.getConfigurationSection(key);
            if (section == null) continue;

            double buyPrice = section.getDouble("buy-price", -1.0);
            double sellPrice = section.getDouble("sell-price", -1.0);

            priceMap.put(material, new ItemPrice(material, buyPrice, sellPrice));
        }

        plugin.getLogger().info("Loaded " + priceMap.size() + " item prices from prices.yml.");
    }

    /**
     * Saves current in-memory prices back to prices.yml.
     */
    public synchronized boolean savePrices() {
        if (pricesConfig == null) {
            pricesConfig = new YamlConfiguration();
        }

        for (Map.Entry<Material, ItemPrice> entry : priceMap.entrySet()) {
            String path = entry.getKey().name();
            pricesConfig.set(path + ".buy-price", entry.getValue().buyPrice());
            pricesConfig.set(path + ".sell-price", entry.getValue().sellPrice());
        }

        try {
            pricesConfig.save(pricesFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save prices.yml to disk!", e);
            return false;
        }
    }

    public Optional<ItemPrice> getPrice(Material material) {
        return Optional.ofNullable(priceMap.get(material));
    }

    public boolean canBuy(Material material) {
        ItemPrice price = priceMap.get(material);
        return price != null && price.canBuy();
    }

    public boolean canSell(Material material) {
        ItemPrice price = priceMap.get(material);
        return price != null && price.canSell();
    }

    public double getBuyPrice(Material material) {
        ItemPrice price = priceMap.get(material);
        return price != null ? price.buyPrice() : -1.0;
    }

    public double getSellPrice(Material material) {
        ItemPrice price = priceMap.get(material);
        return price != null ? price.sellPrice() : -1.0;
    }

    public void setBuyPrice(Material material, double newBuyPrice) {
        ItemPrice current = priceMap.getOrDefault(material, new ItemPrice(material, -1.0, -1.0));
        priceMap.put(material, new ItemPrice(material, newBuyPrice, current.sellPrice()));

        if (pricesConfig != null) {
            pricesConfig.set(material.name() + ".buy-price", newBuyPrice);
            pricesConfig.set(material.name() + ".sell-price", current.sellPrice());
            savePrices();
        }
    }

    public void setSellPrice(Material material, double newSellPrice) {
        ItemPrice current = priceMap.getOrDefault(material, new ItemPrice(material, -1.0, -1.0));
        priceMap.put(material, new ItemPrice(material, current.buyPrice(), newSellPrice));

        if (pricesConfig != null) {
            pricesConfig.set(material.name() + ".buy-price", current.buyPrice());
            pricesConfig.set(material.name() + ".sell-price", newSellPrice);
            savePrices();
        }
    }

    public Set<Material> getConfiguredMaterials() {
        return Collections.unmodifiableSet(priceMap.keySet());
    }

    public int getLoadedPricesCount() {
        return priceMap.size();
    }
}