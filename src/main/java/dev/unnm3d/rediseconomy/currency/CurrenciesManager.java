package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.api.RedisEconomyAPI;
import dev.unnm3d.rediseconomy.config.ConfigManager;
import dev.unnm3d.rediseconomy.config.CurrencySettings;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import dev.unnm3d.rediseconomy.storage.FileStorageService;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;


public class CurrenciesManager extends RedisEconomyAPI implements Listener {
    private final RedisEconomyPlugin plugin;
    @Getter
    private final CompletableFuture<Void> completeMigration;
    private final ConfigManager configManager;
    @Getter
    @Nullable
    private final RedisManager redisManager;
    private final FileStorageService fileStorageService;
    private final boolean fileStorage;
    private final EconomyExchange exchange;
    private final HashMap<String, Currency> currencies;
    @Getter
    private final ConcurrentHashMap<String, UUID> nameUniqueIds;
    private final ConcurrentHashMap<UUID, List<UUID>> lockedAccounts;
    private volatile boolean dirty;


    public CurrenciesManager(@Nullable RedisManager redisManager, @Nullable FileStorageService fileStorageService, RedisEconomyPlugin plugin, ConfigManager configManager) {
        INSTANCE = this;
        this.completeMigration = new CompletableFuture<>();
        this.redisManager = redisManager;
        this.fileStorageService = fileStorageService;
        this.fileStorage = plugin.isFileStorage();
        this.exchange = new EconomyExchange(plugin);
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencies = new HashMap<>();
        try {
            if (fileStorage && fileStorageService != null) {
                this.nameUniqueIds = new ConcurrentHashMap<>(fileStorageService.loadNameUniqueIds());
                this.lockedAccounts = new ConcurrentHashMap<>(fileStorageService.loadLockedAccounts());
                this.dirty = true;
            } else {
                this.nameUniqueIds = loadRedisNameUniqueIds().toCompletableFuture().get(plugin.getConfigManager().getSettings().redis.timeout(), TimeUnit.MILLISECONDS);
                this.lockedAccounts = loadLockedAccounts().toCompletableFuture().get(plugin.getConfigManager().getSettings().redis.timeout(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        loadCurrencySystem(false);

        if (plugin.settings().migrationEnabled) return;

        //Register default currency (which is skipped when loadCurrencySystem) into Vault API
        if (plugin.getServer().getServicesManager().getRegistrations(net.milkbowl.vault.economy.Economy.class)
                .stream().anyMatch(registration -> registration.getPlugin().getName().equals("RedisEconomy"))) {
            plugin.getLogger().severe("Reloading the plugin with a PluginManager may cause issues with Vault-registered currencies! Please restart the server instead.");
        }
        loadDefaultCurrency(plugin.getVaultPlugin());

        if (!fileStorage) {
            registerPayMsgChannel();
            registerBlockAccountChannel();
            registerUpdateChannelPattern();
        }
    }

    public boolean isFileStorage() {
        return fileStorage;
    }

    public void markDirty() {
        if (fileStorage) {
            dirty = true;
        }
    }

    public boolean consumeDirtyFlag() {
        boolean current = dirty;
        dirty = false;
        return current;
    }

    public ConcurrentHashMap<UUID, List<UUID>> getLockedAccountsMap() {
        return lockedAccounts;
    }

    public void loadCurrencySystem(boolean isReload) {
        for (CurrencySettings currencySettings : configManager.getSettings().currencies) {
            //Do not register the default currency twice on Vault or the plugins that rely on it will malfunction
            if (isReload && currencySettings.getCurrencyName().equals(configManager.getSettings().defaultCurrencyName))
                continue;

            Currency currency;
            if (currencySettings.isBankEnabled()) {
                currency = new CurrencyWithBanks(this, currencySettings, fileStorage ? fileStorageService : null);
            } else {
                currency = new Currency(this, currencySettings, fileStorage ? fileStorageService : null);
            }

            //Replace existing currency with updated settings and terminate the old executors
            Optional.ofNullable(currencies.put(currencySettings.getCurrencyName(), currency))
                    .ifPresent(oldCurrency -> oldCurrency.updateExecutors.forEach(executor -> {
                        try {
                            if (!executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                                executor.shutdownNow();
                            }
                        } catch (InterruptedException e1) {
                            executor.shutdownNow();
                        }
                    }));
        }
        //Remove currencies that are not in the config anymore
        currencies.forEach((name, currency) -> {
            if (configManager.getSettings().currencies.stream().noneMatch(cs -> cs.getCurrencyName().equals(name))) {
                currencies.remove(name);
                currency.terminateExecutors();
            }
        });
        if (currencies.get(configManager.getSettings().defaultCurrencyName) == null) {
            currencies.put(configManager.getSettings().defaultCurrencyName, new Currency(this,
                    new CurrencySettings(configManager.getSettings().defaultCurrencyName,
                            "€", "€",
                            "#.##", "en-US",
                            0.0, Double.POSITIVE_INFINITY,
                            0.0, true,
                            -1, true,
                            false, 2), fileStorage ? fileStorageService : null));
        }
        if (fileStorage) {
            markDirty();
        }
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
        plugin.getLogger().info("Successfully registered RedisEconomy default currency '" + getDefaultCurrency().getCurrencyName() + "' as economy provider in Vault");
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
        markDirty();
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
            markDirty();
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
        currencyReset.getOrderedAccounts(Integer.MAX_VALUE).thenAccept(accounts -> {
            for (ScoredValue<String> account : accounts) {
                UUID uuid = UUID.fromString(account.getValue());
                if (!nameUniqueIds.containsValue(uuid)) {
                    currencyReset.setPlayerBalance(uuid, null, 0.0);
                }
            }
        });

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
                    RedisEconomyPlugin.debug("Couldn't find username for UUID " + uuid + " in cache!");
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
                            RedisEconomyPlugin.debug("00 Loaded " + e.getPlayer().getName() + "'s balance of " + balance + " " + currency.getCurrencyName());

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

    private CompletionStage<ConcurrentHashMap<String, UUID>> loadRedisNameUniqueIds() {
        return redisManager.getConnectionAsync(connection ->
                connection.hgetall(NAME_UUID.toString())
                        .thenApply(result -> {
                            ConcurrentHashMap<String, UUID> nameUUIDs = new ConcurrentHashMap<>();
                            result.forEach((name, uuid) -> nameUUIDs.put(name, UUID.fromString(uuid)));
                            RedisEconomyPlugin.debug("start0 Loaded " + nameUUIDs.size() + " name-uuid pairs");
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
        if (fileStorage) {
            markDirty();
            return;
        }
        String[] toRemoveArray = toRemove.keySet().toArray(new String[0]);
        for (int i = 0; i < toRemoveArray.length; i++) {
            if (toRemoveArray[i] == null) {
                toRemoveArray[i] = "null";
            }
        }
        redisManager.getConnectionAsync(connection ->
                connection.hdel(NAME_UUID.toString(), toRemoveArray).thenAccept(integer ->
                        RedisEconomyPlugin.debug("purge0 Removed " + integer + " name-uuid pairs")));
    }

    private void registerPayMsgChannel() {
        StatefulRedisPubSubConnection<String, String> connection = redisManager.getPubSubConnection();
        connection.addListener(new RedisEconomyListener() {
            @Override
            public void message(String channel, String message) {
                String[] args = message.split(";;");
                String sender = args[0];
                String target = args[1];
                String currencyAmount = args[2];
                Player online = plugin.getServer().getPlayerExact(target);
                if (online != null) {
                    if (online.isOnline()) {
                        configManager.getLangs().send(online, configManager.getLangs().payReceived
                                .replace("%player%", sender)
                                .replace("%amount%", currencyAmount));
                        RedisEconomyPlugin.debug("02b Received pay message to " + online.getName() + " timestamp: " + System.currentTimeMillis());

                    }
                }
            }
        });
        connection.async().subscribe(MSG_CHANNEL.toString());
        RedisEconomyPlugin.debug("start2 Registered pay message channel");

    }

    /**
     * Switches the currency accounts of two currencies
     *
     * @param currency    The currency to switch
     * @param newCurrency The new currency to switch to
     */
    public void switchCurrency(Currency currency, Currency newCurrency) {
        if (fileStorage) {
            plugin.getLogger().warning("Switch currency is not supported in file storage mode.");
            return;
        }
        redisManager.getConnectionPipeline(asyncCommands -> {
            asyncCommands.copy(RedisKeys.BALANCE_PREFIX + currency.getCurrencyName(),
                            RedisKeys.BALANCE_PREFIX + currency.getCurrencyName() + "_backup")
                    .thenAccept(success -> RedisEconomyPlugin.debug("Switch0 - Backup currency accounts: " + success));

            asyncCommands.rename(RedisKeys.BALANCE_PREFIX + newCurrency.getCurrencyName(),
                    RedisKeys.BALANCE_PREFIX + currency.getCurrencyName()).thenAccept(success ->
                    RedisEconomyPlugin.debug("Switch1 - Overwrite new currency key with the old one: " + success));

            asyncCommands.renamenx(RedisKeys.BALANCE_PREFIX + currency.getCurrencyName() + "_backup",
                    RedisKeys.BALANCE_PREFIX + newCurrency.getCurrencyName()).thenAccept(success ->
                    RedisEconomyPlugin.debug("Switch2 - Write the backup on the new currency key: " + success));
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

        if (fileStorage) {
            lockedAccounts.put(uuid, locked);
            markDirty();
            future.complete(!isLocked);
            return future;
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
                                RedisEconomyPlugin.debug("Lock0 - Published update locked accounts message: " + integer);

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
        connection.addListener(new RedisEconomyListener() {
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
                    RedisEconomyPlugin.debug("Lockupdate Registered locked accounts for " + account);

                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("Lockupdate Received invalid uuid: " + args[0]);
                }
            }
        });
        connection.async().subscribe(UPDATE_LOCKED_ACCOUNTS.toString());
        RedisEconomyPlugin.debug("Lockch Registered locked accounts channel");

    }

    private void registerUpdateChannelPattern() {
        StatefulRedisPubSubConnection<String, String> connection = redisManager.getPubSubConnection();
        connection.addListener(new RedisEconomyListener() {
            @Override
            public void message(String pattern, String channel, String message) {
                String[] split = message.split(";;");
                if (split.length < 3) {
                    Bukkit.getLogger().severe("Invalid message received from RedisEco channel, consider updating RedisEconomy");
                }
                //0 instanceID, 1 playerUUID, 2 playerName/maxBal, 3 balance/emtpty/empty (if applicable)
                if (split[0].equals(RedisEconomyPlugin.getInstanceUUID().toString())) return;
                final String[] updateArgs = Arrays.copyOfRange(split, 1, split.length);
                for (Currency currency : getCurrencies()) {
                    String name = currency.getCurrencyName();
                    if (channel.endsWith(name)) {
                        currency.processUpdateMessage(channel.substring(0, channel.length() - name.length()), updateArgs);
                        return;
                    }
                }
            }
        });
        connection.async().psubscribe(RedisKeys.UPDATE_PLAYER_CHANNEL_PREFIX.wildcard(), RedisKeys.UPDATE_MAX_BAL_PREFIX.wildcard(),
                RedisKeys.UPDATE_BANK_CHANNEL_PREFIX.wildcard(), UPDATE_BANK_OWNER_CHANNEL_PREFIX.wildcard());
        RedisEconomyPlugin.debug("start1b Listening to RedisEco channel " + RedisKeys.UPDATE_PLAYER_CHANNEL_PREFIX.wildcard());

    }

    public void terminate() {
        currencies.values().forEach(currency -> {
            currency.updateExecutors.forEach(ex -> {
                try {
                    if (!ex.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                        ex.shutdownNow();
                    }
                } catch (InterruptedException e1) {
                    ex.shutdownNow();
                }
            });
        });
        exchange.terminate();
    }
}
