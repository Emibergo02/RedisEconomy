package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.api.RedisEconomyAPI;
import dev.unnm3d.rediseconomy.config.ConfigManager;
import dev.unnm3d.rediseconomy.config.Settings;
import dev.unnm3d.rediseconomy.currency.migration.OfflinePlayerCurrencyMigration;
import dev.unnm3d.rediseconomy.currency.migration.SqlCurrencyMigration;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;


public class CurrenciesManager extends RedisEconomyAPI implements Listener {
    private final RedisEconomyPlugin plugin;
    @Getter
    private final CompletableFuture<Void> completeMigration;
    private final ConfigManager configManager;
    @Getter
    private final RedisManager redisManager;
    private final EconomyExchange exchange;
    private final HashMap<String, Currency> currencies;
    @Getter
    private final ConcurrentHashMap<String, UUID> nameUniqueIds;
    private final ConcurrentHashMap<UUID, List<UUID>> lockedAccounts;


    public CurrenciesManager(RedisManager redisManager, RedisEconomyPlugin plugin, ConfigManager configManager) {
        INSTANCE = this;
        this.completeMigration = new CompletableFuture<>();
        this.redisManager = redisManager;
        this.exchange = new EconomyExchange(plugin);
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencies = new HashMap<>();
        try {
            this.nameUniqueIds = loadRedisNameUniqueIds().toCompletableFuture().get(10, TimeUnit.SECONDS);
            this.lockedAccounts = loadLockedAccounts().toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        configManager.getSettings().currencies.forEach(currencySettings -> {
            Currency currency;
            if (currencySettings.bankEnabled()) {
                currency = new CurrencyWithBanks(this, currencySettings);
            } else {
                currency = new Currency(this, currencySettings);
            }
            currencies.put(currencySettings.currencyName(), currency);
        });
        if (currencies.get(configManager.getSettings().defaultCurrencyName) == null) {
            currencies.put(configManager.getSettings().defaultCurrencyName, new Currency(this, new Settings.CurrencySettings(configManager.getSettings().defaultCurrencyName, "€", "€", "#.##", "en-US", 0.0, Double.POSITIVE_INFINITY, 0.0, true, true, false)));
        }
        registerPayMsgChannel();
        registerBlockAccountChannel();

    }

    /**
     * Loads the default currency into the vault economy provider
     * Unregisters the existent economy provider
     *
     * @param vaultPlugin the vault plugin
     */
    public void loadDefaultCurrency(Plugin vaultPlugin) {
        for (RegisteredServiceProvider<Economy> registration : plugin.getServer().getServicesManager().getRegistrations(Economy.class)) {
            plugin.getServer().getServicesManager().unregister(Economy.class, registration.getProvider());
        }
        plugin.getServer().getServicesManager().register(Economy.class, getDefaultCurrency(), vaultPlugin, ServicePriority.High);
    }

    /**
     * Migrates the balances from the existent economy provider to the new one
     */
    public void migrate() {
        final CurrencyMigration migration;
        if (configManager.getSettings().sqlMigration.enabled()) {
            migration = new SqlCurrencyMigration(plugin, getDefaultCurrency());
        } else {
            migration = new OfflinePlayerCurrencyMigration(plugin, getDefaultCurrency());
        }
        migration.migrate();
        configManager.getSettings().migrationEnabled = false;
        configManager.saveConfigs();
    }

    @Override
    public @Nullable Currency getCurrencyByName(@NotNull String name) {
        return currencies.get(name);
    }

    @Override
    public @NotNull EconomyExchange getExchange() {
        return exchange;
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
        return currencies.get(configManager.getSettings().defaultCurrencyName);
    }

    void updateNameUniqueId(String name, UUID uuid) {
        nameUniqueIds.put(name, uuid);
    }

    /**
     * Removes all players with the given name pattern
     *
     * @param namePattern  the pattern to match
     * @param resetBalance if true, the balance of the removed players will be set to 0
     * @return a map of removed players Name-UUID
     */
    public HashMap<String, UUID> removeNamePattern(String namePattern, boolean resetBalance) {
        HashMap<String, UUID> removed = new HashMap<>();
        for (Map.Entry<String, UUID> entry : nameUniqueIds.entrySet()) {
            if (entry.getKey().matches(namePattern)) {
                removed.put(entry.getKey(), entry.getValue());
            }
        }
        nameUniqueIds.entrySet().removeAll(removed.entrySet());
        if (!removed.isEmpty()) {
            removeRedisNameUniqueIds(removed);
            if (resetBalance) {
                for (Currency currency : currencies.values()) {
                    removed.forEach((name, uuid) -> currency.setPlayerBalance(uuid, name, 0.0));
                }
            }
        }
        return removed;
    }

    /**
     * Resets the balance of all players with the given name pattern
     *
     * @param namePattern   the pattern to match
     * @param currencyReset the currency to be reset
     * @return a map of reset players
     */
    public HashMap<String, UUID> resetBalanceNamePattern(String namePattern, Currency currencyReset) {
        HashMap<String, UUID> removed = new HashMap<>();
        for (Map.Entry<String, UUID> entry : nameUniqueIds.entrySet()) {
            if (entry.getKey().matches(namePattern)) {
                removed.put(entry.getKey(), entry.getValue());
            }
        }
        if (!removed.isEmpty()) {
            removed.forEach((name, uuid) -> currencyReset.setPlayerBalance(uuid, name, 0.0));
        }
        return removed;
    }

    @Override
    public @Nullable UUID getUUIDFromUsernameCache(@NotNull String username) {
        return nameUniqueIds.get(username);
    }

    @Override
    public @Nullable String getUsernameFromUUIDCache(@NotNull UUID uuid) {
        if (uuid.equals(RedisKeys.getServerUUID())) return "Server";
        return nameUniqueIds.entrySet().stream()
                .filter(e -> e.getValue().equals(uuid))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> {
                    if (plugin.settings().debug)
                        Bukkit.getLogger().warning("Couldn't find username for UUID " + uuid + " in cache!");
                    return null;
                });
    }

    @Override
    public @NotNull String getCaseSensitiveName(@NotNull String caseInsensitiveName) {
        for (Map.Entry<String, UUID> entry : nameUniqueIds.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(caseInsensitiveName))
                return entry.getKey();
        }
        return caseInsensitiveName;
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
    private void onJoin(PlayerJoinEvent e) {
        getCurrencies().forEach(currency ->
                currency.getAccountRedis(e.getPlayer().getUniqueId())
                        .thenAccept(balance -> {
                            //DEBUG
                            if (configManager.getSettings().debug) {
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

    @EventHandler
    private void onPluginEnable(PluginEnableEvent pluginEnableEvent) {
        if (plugin.settings().migrationEnabled) return;
        if (!plugin.getServer().getServicesManager().getRegistrations(net.milkbowl.vault.economy.Economy.class)
                .stream().allMatch(registration -> registration.getPlugin().equals(plugin))) {
            loadDefaultCurrency(plugin.getVaultPlugin());
        }
    }

    private CompletionStage<ConcurrentHashMap<String, UUID>> loadRedisNameUniqueIds() {
        return redisManager.getConnectionAsync(connection ->
                connection.hgetall(NAME_UUID.toString())
                        .thenApply(result -> {
                            ConcurrentHashMap<String, UUID> nameUUIDs = new ConcurrentHashMap<>();
                            result.forEach((name, uuid) -> nameUUIDs.put(name, UUID.fromString(uuid)));
                            if (configManager.getSettings().debug) {
                                Bukkit.getLogger().info("start0 Loaded " + nameUUIDs.size() + " name-uuid pairs");
                            }
                            return nameUUIDs;
                        })
        );
    }

    private CompletionStage<ConcurrentHashMap<UUID, List<UUID>>> loadLockedAccounts() {
        return redisManager.getConnectionAsync(connection ->
                connection.hgetall(LOCKED_ACCOUNTS.toString())
                        .thenApply(result -> {
                            ConcurrentHashMap<UUID, List<UUID>> lockedAccounts = new ConcurrentHashMap<>();
                            result.forEach((uuid, uuidList) ->
                                    lockedAccounts.put(UUID.fromString(uuid),
                                            new ArrayList<>(Arrays.stream(uuidList.split(","))
                                                    .filter(stringUUID -> stringUUID.length() >= 32)
                                                    .map(UUID::fromString).toList())
                                    )
                            );
                            return lockedAccounts;
                        })
        );
    }

    private void removeRedisNameUniqueIds(Map<String, UUID> toRemove) {
        String[] toRemoveArray = toRemove.keySet().toArray(new String[0]);
        for (int i = 0; i < toRemoveArray.length; i++) {
            if (toRemoveArray[i] == null) {
                toRemoveArray[i] = "null";
            }
        }
        redisManager.getConnectionAsync(connection ->
                connection.hdel(NAME_UUID.toString(), toRemoveArray).thenAccept(integer -> {
                    if (configManager.getSettings().debug) {
                        Bukkit.getLogger().info("purge0 Removed " + integer + " name-uuid pairs");
                    }
                }));
    }

    private void registerPayMsgChannel() {
        StatefulRedisPubSubConnection<String, String> connection = redisManager.getPubSubConnection();
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
                        configManager.getLangs().send(online, configManager.getLangs().payReceived.replace("%player%", sender).replace("%amount%", currencyAmount));
                        if (configManager.getSettings().debug) {
                            plugin.getLogger().info("02b Received pay message to " + online.getName() + " timestamp: " + System.currentTimeMillis());
                        }
                    }
                }
            }
        });
        connection.async().subscribe(MSG_CHANNEL.toString());
        if (configManager.getSettings().debug) {
            Bukkit.getLogger().info("start2 Registered pay message channel");
        }
    }

    /**
     * Switches the currency accounts of two currencies
     *
     * @param currency    The currency to switch
     * @param newCurrency The new currency to switch to
     */
    public void switchCurrency(Currency currency, Currency newCurrency) {
        redisManager.getConnectionPipeline(asyncCommands -> {
            asyncCommands.copy(RedisKeys.BALANCE_PREFIX + currency.getCurrencyName(), RedisKeys.BALANCE_PREFIX + currency.getCurrencyName() + "_backup").thenAccept(success -> {
                if (configManager.getSettings().debug) {
                    Bukkit.getLogger().info("Switch0 - Backup currency accounts: " + success);
                }
            });
            asyncCommands.rename(RedisKeys.BALANCE_PREFIX + newCurrency.getCurrencyName(), RedisKeys.BALANCE_PREFIX + currency.getCurrencyName()).thenAccept(success -> {
                if (configManager.getSettings().debug) {
                    Bukkit.getLogger().info("Switch1 - Overwrite new currency key with the old one: " + success);
                }
            });
            asyncCommands.renamenx(RedisKeys.BALANCE_PREFIX + currency.getCurrencyName() + "_backup", RedisKeys.BALANCE_PREFIX + newCurrency.getCurrencyName()).thenAccept(success -> {
                if (configManager.getSettings().debug) {
                    Bukkit.getLogger().info("Switch2 - Write the backup on the new currency key: " + success);
                }
            });
            return null;
        });
    }

    /**
     * Toggles the lock status of an account
     *
     * @param uuid   The uuid of the player
     * @param target The uuid of the target
     * @return completable future true if the account is locked, false if it is unlocked
     */
    public CompletableFuture<Boolean> toggleAccountLock(@NotNull UUID uuid, UUID target) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        List<UUID> locked = lockedAccounts.getOrDefault(uuid, new ArrayList<>());
        boolean isLocked = locked.contains(target);

        if (isLocked) {
            locked.remove(target);
        } else {
            locked.add(target);
        }

        //The string to be stored into Redis
        String redisString = locked.stream().map(UUID::toString).collect(Collectors.joining(","));

        redisManager.getConnectionPipeline(connection -> {
                    connection.hset(LOCKED_ACCOUNTS.toString(),
                            uuid.toString(),
                            redisString
                    );
                    connection.publish(UPDATE_LOCKED_ACCOUNTS.toString(), uuid + "," + redisString)
                            .thenAccept(integer -> {
                                if (configManager.getSettings().debug) {
                                    Bukkit.getLogger().info("Lock0 - Published update locked accounts message: " + integer);
                                }
                                lockedAccounts.put(uuid, locked);
                                future.complete(!isLocked);
                            });
                    return null;
                }
        );

        return future;
    }

    public boolean isAccountLocked(UUID uuid, UUID target) {
        return getLockedAccounts(uuid).contains(target) ||
                getLockedAccounts(uuid).contains(RedisKeys.getAllAccountUUID());
    }

    /**
     * Formats the amount string to a double
     * Parses suffixes (10k, 10M for 10 thousand and 10 million)
     * Parses percentages ("10%" for 10% of the accountOwner balance)
     *
     * @param targetName The account that has to be modified
     * @param currency   the currency to format the amount
     * @param amount     the amount to format
     * @return the formatted amount
     */
    public double formatAmountString(String targetName, Currency currency, String amount) {
        try {
            //Check if last char of amount is number
            if (Character.isDigit(amount.charAt(amount.length() - 1))) {
                return Double.parseDouble(amount);
            }
            double parsedAmount = Double.parseDouble(amount.substring(0, amount.length() - 1));
            return amount.endsWith("%") && plugin.settings().allowPercentagePayments ?
                    //Percentage 20%
                    parsedAmount / 100 * currency.getBalance(targetName) :
                    //Parse suffixes from map
                    parsedAmount * plugin.langs().getSuffixes()
                            .getOrDefault(amount.substring(amount.length() - 1), -1L);
        } catch (NumberFormatException e) {
            plugin.langs().send(Bukkit.getConsoleSender(), plugin.langs().invalidAmount);
        }
        return -1;
    }

    public List<UUID> getLockedAccounts(UUID uuid) {
        return lockedAccounts.getOrDefault(uuid, new ArrayList<>());
    }

    private void registerBlockAccountChannel() {
        StatefulRedisPubSubConnection<String, String> connection = redisManager.getPubSubConnection();
        connection.addListener(new RedisCurrencyListener() {
            @Override
            public void message(String channel, String message) {
                String[] args = message.split(",");
                try {
                    UUID account = UUID.fromString(args[0]);
                    if (args.length == 1 || args[1].isEmpty()) {
                        lockedAccounts.remove(account);
                        return;
                    }
                    List<UUID> newLockedAccounts = new ArrayList<>();
                    for (int i = 1; i < args.length; i++) {
                        newLockedAccounts.add(UUID.fromString(args[i]));
                    }
                    lockedAccounts.put(account, newLockedAccounts);
                    if (configManager.getSettings().debug) {
                        Bukkit.getLogger().info("Lockupdate Registered locked accounts for " + account);
                    }
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("Lockupdate Received invalid uuid: " + args[0]);
                }
            }
        });
        connection.async().subscribe(UPDATE_LOCKED_ACCOUNTS.toString());
        if (configManager.getSettings().debug) {
            Bukkit.getLogger().info("Lockch Registered locked accounts channel");
        }
    }

}
