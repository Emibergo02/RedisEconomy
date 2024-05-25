package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

        plugin.langs().send(Bukkit.getConsoleSender(), plugin.langs().migrationStart.replace("%provider%", getProvider()));

        start();

        plugin.langs().send(Bukkit.getConsoleSender(), plugin.langs().migrationCompleted);
    }

    protected void updateAccountLocal(@NotNull UUID uuid, @Nullable String playerName, double balance) {
        currency.updateAccountLocal(uuid, playerName, balance);
    }
}
