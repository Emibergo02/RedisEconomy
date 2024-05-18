package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import org.bukkit.Bukkit;

public abstract class CurrencyMigration {

    protected final RedisEconomyPlugin plugin;
    protected final Currency currency;

    public CurrencyMigration(RedisEconomyPlugin plugin, Currency currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    public abstract String getProvider();

    protected abstract boolean setup();

    protected abstract void start();

    public void migrate() {
        if (!setup()) {
            return;
        }

        Bukkit.getConsoleSender().sendMessage("[RedisEconomy] §aMigrating from " + getProvider() + "...");

        start();

        Bukkit.getConsoleSender().sendMessage("[RedisEconomy] §aMigration completed!");
        Bukkit.getConsoleSender().sendMessage("[RedisEconomy] §aRestart the server to apply the changes.");
    }
}
