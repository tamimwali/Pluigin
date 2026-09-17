package com.mineplugin.itemshop.price;

import org.bukkit.Material;

/**
 * Modern Java 21 record for item pricing.
 * Negative or 0 prices designate disabled status.
 */
public record ItemPrice(Material material, double buyPrice, double sellPrice) {

    public boolean canBuy() {
        return buyPrice > 0.0;
    }

    public boolean canSell() {
        return sellPrice > 0.0;
    }

    public double getBuyCost(int amount) {
        return buyPrice * amount;
    }

    public double getSellEarnings(int amount) {
        return sellPrice * amount;
    }
}