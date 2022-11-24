package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.api.RedisEconomyAPI;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import io.lettuce.core.ScoredValue;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
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

import static dev.unnm3d.rediseconomy.redis.RedisKeys.MSG_CHANNEL;
import static dev.unnm3d.rediseconomy.redis.RedisKeys.NAME_UUID;


public class CurrenciesManager extends RedisEconomyAPI implements Listener {
    private final RedisEconomyPlugin plugin;
    @Getter
    private final RedisManager redisManager;
    private final HashMap<String, Currency> currencies;
    @Getter
    private final ConcurrentHashMap<String, UUID> nameUniqueIds;

    public CurrenciesManager(RedisManager redisManager, RedisEconomyPlugin plugin) {
        INSTANCE = this;
        this.redisManager = redisManager;
        this.plugin = plugin;
        this.currencies = new HashMap<>();
        this.nameUniqueIds = loadRedisNameUniqueIds();
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
        registerPayMsgChannel();

    }


    public void loadDefaultCurrency(Plugin vaultPlugin) {
        Currency defaultCurrency = currencies.get("vault");
        if (plugin.getConfig().getBoolean("migration-enabled", false)) {
            RegisteredServiceProvider<Economy> existentProvider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (existentProvider == null) {
                plugin.getLogger().severe("Vault economy provider not found!");
                return;
            }
            CompletableFuture.supplyAsync(() -> {
                plugin.getLogger().info("§aMigrating from " + existentProvider.getProvider().getName() + "...");
                if (existentProvider.getProvider() == defaultCurrency) {
                    plugin.getLogger().info("There's no other provider apart RedisEconomy!");
                    return defaultCurrency;
                }

                List<ScoredValue<String>> balances = new ArrayList<>();
                Map<String, String> nameUniqueIds = new HashMap<>();
                for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    double bal = existentProvider.getProvider().getBalance(offlinePlayer);
                    balances.add(ScoredValue.just(bal, offlinePlayer.getUniqueId().toString()));
                    nameUniqueIds.put(offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName(), offlinePlayer.getUniqueId().toString());
                    defaultCurrency.updateAccountLocal(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName(), bal);
                }

                defaultCurrency.updateAccountsCloudCache(balances, nameUniqueIds);
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
            //DEBUG
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("00 Loaded " + e.getPlayer().getName() + "'s balance of " + balance + " " + currency.getCurrencyName());
            }
            if (balance == null) {
                currency.createPlayerAccount(e.getPlayer());
            } else {
                currency.updateAccountLocal(e.getPlayer().getUniqueId(), e.getPlayer().getName(), balance);
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        }));
    }

    private ConcurrentHashMap<String, UUID> loadRedisNameUniqueIds() {

        return redisManager.getConnection(connection -> {
            ConcurrentHashMap<String, UUID> nameUUIDs = new ConcurrentHashMap<>();
            connection.sync().hgetall(NAME_UUID.toString()).forEach((name, uuid) -> nameUUIDs.put(name, UUID.fromString(uuid)));
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("start0 Loaded " + nameUUIDs.size() + " name-uuid pairs");
            }
            return nameUUIDs;
        });
    }

    private void registerPayMsgChannel() {
        redisManager.getPubSubConnection(connection -> {
            connection.addListener(new RedisCurrencyListener() {
                @Override
                public void message(String channel, String message) {
                    String[] args = message.split(";;");
                    String sender = args[0];
                    String target = args[1];
                    String currencyAmount = args[2];
                    Player online = plugin.getServer().getPlayer(target);
                    if (online != null) {
                        if (online.isOnline()) {
                            plugin.getSettings().send(online, plugin.getSettings().PAY_RECEIVED.replace("%player%", sender).replace("%amount%", currencyAmount));
                            if (RedisEconomyPlugin.settings().DEBUG) {
                                plugin.getLogger().info("02b Received pay message to " + online.getName() + " timestamp: " + System.currentTimeMillis());
                            }
                        }
                    }
                }
            });
            connection.async().subscribe(MSG_CHANNEL.toString());
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("start2 Registered pay message channel");
            }
        });

    }
}
