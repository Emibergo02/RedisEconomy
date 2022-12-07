package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;

@AllArgsConstructor
public class Currency implements Economy {
    private final CurrenciesManager currenciesManager;

    @Getter
    private final String currencyName;
    private final ConcurrentHashMap<UUID, Double> accounts;
    private boolean enabled;
    @Getter
    private String currencySingular;
    @Getter
    private String currencyPlural;
    @Getter
    private double startingBalance;
    @Getter
    private double transactionTax;


    /**
     * Creates a new currency.
     * Currency implements Economy from Vault, so it's the same as using any other Vault Economy plugin
     *
     * @param currenciesManager The CurrenciesManager instance
     * @param currencyName      The name of the currency
     * @param currencySingular  The singular symbol of the currency
     * @param currencyPlural    The plural symbol of the currency
     * @param startingBalance   The starting balance of the currency
     * @param transactionTax    The transaction tax of the currency
     */
    public Currency(CurrenciesManager currenciesManager, String currencyName, String currencySingular, String currencyPlural, double startingBalance, double transactionTax) {
        this.currenciesManager = currenciesManager;
        this.enabled = true;
        this.currencyName = currencyName;
        this.currencySingular = currencySingular;
        this.currencyPlural = currencyPlural;
        this.startingBalance = startingBalance;
        this.transactionTax = transactionTax;
        this.accounts = new ConcurrentHashMap<>();
        getOrderedAccountsSync().forEach(t -> accounts.put(UUID.fromString(t.getValue()), t.getScore()));
        if (RedisEconomyPlugin.settings().DEBUG && accounts.size() > 0) {
            Bukkit.getLogger().info("start1 Loaded " + accounts.size() + " accounts for currency " + currencyName);
        }
        registerUpdateListener();
    }


    private void registerUpdateListener() {
        currenciesManager.getRedisManager().getPubSubConnection(connection -> {
            connection.addListener(new RedisCurrencyListener() {
                @Override
                public void message(String channel, String message) {
                    String[] split = message.split(";;");
                    if (split.length != 4) {
                        Bukkit.getLogger().severe("Invalid message received from RedisEco channel, consider updating RedisEconomy");
                        return;
                    }
                    if (split[0].equals(RedisEconomyPlugin.settings().SERVER_ID)) return;
                    UUID uuid = UUID.fromString(split[1]);
                    String playerName = split[2];
                    double balance = Double.parseDouble(split[3]);
                    updateAccountLocal(uuid, playerName, balance);
                    if (RedisEconomyPlugin.settings().DEBUG) {
                        Bukkit.getLogger().info("01b Received balance update " + playerName + " to " + balance);
                    }
                }
            });
            connection.async().subscribe(UPDATE_CHANNEL_PREFIX + currencyName);
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("start1b Listening to RedisEco channel " + UPDATE_CHANNEL_PREFIX + currencyName);
            }
        });


    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getName() {
        return "RedisEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount) + (amount == 1 ? currencySingular : currencyPlural);
    }

    @Override
    public String currencyNamePlural() {
        return currencyPlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencySingular;
    }

    @Override
    public boolean hasAccount(@NotNull String playerName) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null) return false;
        return hasAccount(playerUniqueId);
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(@NotNull String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    public boolean hasAccount(@NotNull UUID playerUUID) {
        return accounts.containsKey(playerUUID);
    }

    public double getBalance(@NotNull UUID playerUUID) {
        return accounts.get(playerUUID);
    }

    @Override
    public double getBalance(@NotNull String playerName) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null) return 0.0D;
        return getBalance(playerUniqueId);
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(@NotNull String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(@NotNull String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return has(player.getUniqueId(), amount);
    }

    public boolean has(@NotNull UUID playerUUID, double amount) {
        return getBalance(playerUUID) >= amount;
    }

    @Override
    public boolean has(@NotNull String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount) {
        return withdrawPlayer(playerName, amount, "Withdraw");
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, double amount) {
        return withdrawPlayer(player.getUniqueId(), player.getName() == null ? player.getUniqueId() + "-Unknown" : player.getName(), amount, "Withdraw");
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, double amount) {
        return depositPlayer(playerName, amount, "Deposit");
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, double amount) {
        return depositPlayer(player.getUniqueId(), player.getName() == null ? player.getUniqueId() + "-Unknown" : player.getName(), amount, "Deposit");
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return false;
        return createPlayerAccount(playerUUID, playerName);
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player) {
        return createPlayerAccount(player.getUniqueId(), player.getName() == null ? player.getUniqueId() + "-Unknown" : player.getName());
    }

    public boolean createPlayerAccount(@NotNull UUID playerUUID, @NotNull String playerName) {
        if (hasAccount(playerUUID))
            return false;
        updateAccount(playerUUID, playerName, startingBalance);
        return true;
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName, @Nullable String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player, @Nullable String worldName) {
        return createPlayerAccount(player);
    }

    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount, @Nullable String reason) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return withdrawPlayer(playerUniqueId, playerName, amount, reason);
    }

    public EconomyResponse withdrawPlayer(@NotNull UUID playerUUID, @NotNull String playerName, double amount, @Nullable String reason) {
        if (!hasAccount(playerUUID))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (amount * transactionTax);
        if (!has(playerUUID, amountToWithdraw))
            return new EconomyResponse(0, getBalance(playerUUID), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        updateAccount(playerUUID, playerName, getBalance(playerUUID) - amountToWithdraw);
        currenciesManager.getExchange().saveTransaction(playerUUID, null, -amountToWithdraw, currencyName, reason == null ? "Withdraw" : reason);
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @SuppressWarnings("unused")
    public EconomyResponse payPlayer(@NotNull UUID sender, @NotNull UUID receiver, double amount, @Nullable String reason) {
        String senderName = currenciesManager.getUsernameFromUUIDCache(sender);
        if (senderName == null || !hasAccount(sender))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        //same for receiver
        String receiverName = currenciesManager.getUsernameFromUUIDCache(receiver);
        if (receiverName == null || !hasAccount(receiver))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (amount * transactionTax);
        if (!has(sender, amountToWithdraw))
            return new EconomyResponse(0, getBalance(sender), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        updateAccount(sender, senderName, getBalance(sender) - amountToWithdraw);
        currenciesManager.getExchange().saveTransaction(sender, receiver, -amountToWithdraw, currencyName, reason == null ? "Payment" : reason);
        updateAccount(sender, receiverName, getBalance(receiver) + amount);
        currenciesManager.getExchange().saveTransaction(receiver, sender, amount, currencyName, reason == null ? "Payment" : reason);

        return new EconomyResponse(amount, getBalance(sender), EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse payPlayer(@NotNull String senderName, @NotNull String receiverName, double amount) {
        if (!hasAccount(senderName))
            return new EconomyResponse(amount, getBalance(senderName), EconomyResponse.ResponseType.FAILURE, "Account not found");
        if (!hasAccount(receiverName))
            return new EconomyResponse(amount, getBalance(receiverName), EconomyResponse.ResponseType.FAILURE, "Account not found");
        UUID sender = currenciesManager.getUUIDFromUsernameCache(senderName);
        UUID receiver = currenciesManager.getUUIDFromUsernameCache(receiverName);
        double amountToWithdraw = amount + (amount * transactionTax);
        if (sender == null || receiver == null)
            return new EconomyResponse(amount, getBalance(senderName), EconomyResponse.ResponseType.FAILURE, "Account not found");
        if (!has(senderName, amountToWithdraw))
            return new EconomyResponse(0, getBalance(senderName), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        updateAccount(sender, senderName, getBalance(sender) - amountToWithdraw);
        updateAccount(receiver, receiverName, getBalance(receiver) + amount);

        return new EconomyResponse(amount, getBalance(sender), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Set the balance of a player
     *
     * @param player The player to set the balance of
     * @param amount The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(@NotNull OfflinePlayer player, double amount) {
        return setPlayerBalance(player.getUniqueId(), player.getName() == null ? player.getUniqueId() + "-Unknown" : player.getName(), amount);
    }

    /**
     * Set the balance of a player
     *
     * @param playerUUID The player uuid to set the balance of
     * @param amount     The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(@NotNull UUID playerUUID, @NotNull String playerName, double amount) {
        currenciesManager.getExchange().saveTransaction(playerUUID, null, -getBalance(playerUUID), currencyName, "Reset balance");
        updateAccount(playerUUID, playerName, amount);
        currenciesManager.getExchange().saveTransaction(playerUUID, null, amount, currencyName, "Set balance");
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Set the balance of a player
     *
     * @param playerName The player to set the balance of
     * @param amount     The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(@NotNull String playerName, double amount) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return setPlayerBalance(playerUUID, playerName, amount);
    }

    public EconomyResponse depositPlayer(@NotNull String playerName, double amount, @Nullable String reason) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return depositPlayer(playerUUID, playerName, amount, reason);
    }

    public EconomyResponse depositPlayer(@NotNull UUID playerUUID, @NotNull String playerName, double amount, String reason) {
        if (!hasAccount(playerUUID))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccount(playerUUID, playerName, getBalance(playerUUID) + amount);
        currenciesManager.getExchange().saveTransaction(playerUUID, null, amount, currencyName, reason == null ? "Deposit" : reason);
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    void updateAccountLocal(UUID uuid, String playerName, double balance) {
        currenciesManager.updateNameUniqueId(playerName, uuid);
        accounts.put(uuid, balance);
    }

    private void updateAccount(@NotNull UUID uuid, @NotNull String playerName, double balance) {
        updateAccountCloudCache(uuid, playerName, balance, 0);
        updateAccountLocal(uuid, playerName, balance);
    }

    private void updateAccountCloudCache(@NotNull UUID uuid, @NotNull String playerName, double balance, int tries) {
        try {
            currenciesManager.getRedisManager().getConnection(connection -> {
                connection.setTimeout(Duration.ofMillis(300));
                RedisAsyncCommands<String, String> commands = connection.async();
                connection.setAutoFlushCommands(false);
                commands.zadd(BALANCE_PREFIX + currencyName, balance, uuid.toString());
                commands.hset(NAME_UUID.toString(), playerName, uuid.toString());
                commands.publish(UPDATE_CHANNEL_PREFIX + currencyName, RedisEconomyPlugin.settings().SERVER_ID + ";;" + uuid + ";;" + playerName + ";;" + balance).thenAccept((result) -> {
                    if (RedisEconomyPlugin.settings().DEBUG) {
                        Bukkit.getLogger().info("01 Sent update account " + playerName + " to " + balance);
                    }
                });
                connection.flushCommands();
                return null;
            });

        } catch (Exception e) {
            if (tries < 3) {
                e.printStackTrace();
                Bukkit.getLogger().severe("Failed to update account " + playerName + " after 3 tries");
                Bukkit.getLogger().severe("Player accounts are desynchronized");
                updateAccountCloudCache(uuid, playerName, balance, tries + 1);
            } else {
                e.printStackTrace();
            }
        }

    }

    @SuppressWarnings("unchecked")
    void updateAccountsCloudCache(List<ScoredValue<String>> balances, Map<String, String> nameUUIDs) {
        StatefulRedisConnection<String, String> connection = currenciesManager.getRedisManager().getUnclosedConnection();
        RedisAsyncCommands<String, String> commands = connection.async();
        connection.setAutoFlushCommands(false);
        ScoredValue<String>[] balancesArray = new ScoredValue[balances.size()];
        balances.toArray(balancesArray);

        RedisFuture<Long> sortedAddFuture = commands.zadd(BALANCE_PREFIX + currencyName, balancesArray);
        RedisFuture<Long> hstFuture = commands.hset(NAME_UUID.toString(), nameUUIDs);

        connection.flushCommands();
        try {
            Bukkit.getLogger().info("migration01 updated balances into " + BALANCE_PREFIX + currencyName + " accounts. result " + sortedAddFuture.get(20, TimeUnit.SECONDS));
            Bukkit.getLogger().info("migration02 updated nameuuids into " + NAME_UUID + " accounts. result " + hstFuture.get(20, TimeUnit.SECONDS));
            connection.close();
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get ordered accounts from Redis
     * Redis uses ordered sets as data structures
     * This method has a time complexity of O(log(N)+M) with N being the number of elements in the sorted set and M the number of elements returned.
     *
     * @return A list of accounts ordered by balance in Tuples of UUID and balance (UUID is stringified)
     */
    public CompletionStage<List<ScoredValue<String>>> getOrderedAccounts() {
        return CompletableFuture.supplyAsync(this::getOrderedAccountsSync);
    }

    private List<ScoredValue<String>> getOrderedAccountsSync() {
        return currenciesManager.getRedisManager().getConnection(connection -> connection.sync().zrevrangeWithScores(BALANCE_PREFIX + currencyName, 0, -1));
    }

    /**
     * Get single ordered account from Redis
     *
     * @param uuid The UUID of the account
     * @return The balance associated with the UUID on Redis
     */
    public CompletableFuture<Double> getAccountRedis(UUID uuid) {
        return currenciesManager.getRedisManager().getConnection(connection -> connection.async().zscore(BALANCE_PREFIX + currencyName, uuid.toString()).toCompletableFuture());
    }

    /**
     * Get all accounts in cache
     *
     * @return Unmodifiable map of accounts
     */
    @SuppressWarnings("unused")
    public final Map<UUID, Double> getAccounts() {
        return Collections.unmodifiableMap(accounts);
    }
}
