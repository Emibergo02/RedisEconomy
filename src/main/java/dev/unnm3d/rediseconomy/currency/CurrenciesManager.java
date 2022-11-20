package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.api.RedisEconomyAPI;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;


public class CurrenciesManager extends RedisEconomyAPI implements Listener {
    private final RedisEconomyPlugin plugin;
    @Getter
    private final RedisClient redisClient;
    private final HashMap<String, Currency> currencies;
    @Getter
    private final ConcurrentHashMap<String, UUID> nameUniqueIds;

    public CurrenciesManager(RedisClient redisClient, RedisEconomyPlugin plugin) {
        INSTANCE = this;
        this.redisClient = redisClient;
        this.plugin = plugin;
        this.currencies = new HashMap<>();
        this.nameUniqueIds=loadRedisNameUniqueIds();
        ConfigurationSection configurationSection = plugin.getConfig().getConfigurationSection("currencies");
        if (configurationSection != null)
            for (String key : configurationSection.getKeys(false)) {
                String singleSymbol = configurationSection.getString(key + ".currency-single", "€");
                String pluralSymbol = configurationSection.getString(key + ".currency-plural", "€");
                double starting = configurationSection.getDouble(key + ".starting-balance", 0.0);
                double tax = configurationSection.getDouble(key + ".pay-tax", 0.0);
                Currency currency = new Currency(this, key, singleSymbol, pluralSymbol, starting, tax);
                currencies.put(key, currency);
            }
        if (currencies.get("vault") == null) {
            currencies.put("vault", new Currency(this, "vault", "€", "€", 0.0, 0.0));
        }

    }


    public void loadDefaultCurrency(Plugin vaultPlugin) {
        Currency defaultCurrency = currencies.get("vault");
        if (plugin.getConfig().getBoolean("migration-enabled", false)) {

            @NotNull Collection<RegisteredServiceProvider<Economy>> existentProviders = plugin.getServer().getServicesManager().getRegistrations(Economy.class);
            CompletableFuture.supplyAsync(() -> {
                Bukkit.getLogger().info("§aStarting migration from " + existentProviders.size() + " providers...");
                    existentProviders.forEach(reg -> {
                        Bukkit.getLogger().info("§aMigrating from " + reg.getProvider().getName() + "...");
                        if (reg.getProvider() != defaultCurrency) {
                            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                double bal = reg.getProvider().getBalance(offlinePlayer);
                                defaultCurrency.setPlayerBalance(offlinePlayer, bal);
                                Bukkit.getLogger().info("Migrated " + offlinePlayer.getName() + "'s balance of " + bal);
                            }
                        }
                    });
                Bukkit.getLogger().info("§aMigration finished");
                return defaultCurrency;
            }).thenAccept((vaultCurrency) -> {
                plugin.getServer().getServicesManager().register(Economy.class, vaultCurrency, vaultPlugin, ServicePriority.High);
                plugin.getConfig().set("migration-enabled", false);
                plugin.saveConfig();
            });
        } else
            plugin.getServer().getServicesManager().register(Economy.class, defaultCurrency, vaultPlugin, ServicePriority.High);
    }

    @Override
    public @Nullable Currency getCurrencyByName(@NotNull String name) {
        return currencies.get(name);
    }

    @Override
    public @NotNull Collection<Currency> getCurrencies() {
        return currencies.values();
    }

    @Override
    public @NotNull Map<String, Currency> getCurrenciesWithNames() {
        return Collections.unmodifiableMap(currencies);
    }

    @Override
    public @NotNull Currency getDefaultCurrency() {
        return currencies.get("vault");
    }

    void updateNameUniqueId(String name, UUID uuid) {
        nameUniqueIds.put(name, uuid);
    }

    @Override
    public @Nullable UUID getUUIDFromUsernameCache(@NotNull String username) {
        return nameUniqueIds.get(username);
    }

    @Override
    public @Nullable String getUsernameFromUUIDCache(@NotNull UUID uuid) {
        for (Map.Entry<String, UUID> entry : nameUniqueIds.entrySet()) {
            if (entry.getValue().equals(uuid))
                return entry.getKey();
        }
        return null;
    }

    @Override
    public @Nullable Currency getCurrencyBySymbol(@NotNull String symbol) {
        for (Currency currency : currencies.values()) {
            if (currency.getCurrencySingular().equals(symbol) || currency.getCurrencyPlural().equals(symbol))
                return currency;
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        getCurrencies().forEach(currency -> currency.getAccountRedis(e.getPlayer().getUniqueId()).thenAccept(balance -> {
            System.out.println("Balance of " + e.getPlayer().getName() + " in " + currency.getName() + " is " + balance);
            if (balance == null) {
                currency.createPlayerAccount(e.getPlayer());
            } else {
                currency.updateAccountLocal(e.getPlayer().getUniqueId(), e.getPlayer().getName(), balance);
            }
        }));
    }

    private ConcurrentHashMap<String, UUID> loadRedisNameUniqueIds() {

        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            ConcurrentHashMap<String, UUID> nameUUIDs = new ConcurrentHashMap<>();
            connection.sync().hgetall("rediseco:nameuuid").forEach((name, uuid) -> nameUUIDs.put(name, UUID.fromString(uuid)));
            return nameUUIDs;
        }
        //     .thenAccept(map -> {
           // map.forEach((name, uuid) -> {
           //     this.nameUniqueIds.put(name, UUID.fromString(uuid));
           //     System.out.println(name);
           // }
           // );
           // });

    }
}
