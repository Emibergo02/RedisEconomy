package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.struct.CurrencySettings;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;

public class CurrencyWithBanks extends Currency {
    private final ConcurrentHashMap<String, Double> bankAccounts;
    /**
     * Account ID -> Owner UUID
     */
    private final ConcurrentHashMap<String, UUID> bankOwners;

    public CurrencyWithBanks(CurrenciesManager currenciesManager, CurrencySettings currencySettings) {
        super(currenciesManager, currencySettings);
        bankAccounts = new ConcurrentHashMap<>();
        bankOwners = new ConcurrentHashMap<>();
        getOrderedBankAccounts().thenApply(list -> {
            for (ScoredValue<String> scoredValue : list) {
                bankAccounts.put(scoredValue.getValue(), scoredValue.getScore());
            }
            if (RedisEconomyPlugin.getInstance().settings().debug && bankAccounts.size() > 0) {
                Bukkit.getLogger().info("start1bank Loaded " + bankAccounts.size() + " accounts for currency " + currencyName);
            }
            return list;
        }).toCompletableFuture().join();
        getRedisBankOwners().thenApply(map -> {
            map.forEach((k, v) -> bankOwners.put(k, UUID.fromString(v)));
            if (RedisEconomyPlugin.getInstance().settings().debug && bankOwners.size() > 0) {
                Bukkit.getLogger().info("start1bankb Loaded " + bankOwners.size() + " accounts owners for currency " + currencyName);
            }
            return map;
        }).toCompletableFuture().join();
        registerBankAccountListener();
        registerBankOwnerListener();
    }

    private void registerBankOwnerListener() {
        StatefulRedisPubSubConnection<String, String> connection = currenciesManager.getRedisManager().getPubSubConnection();
        connection.addListener(new RedisCurrencyListener() {
            @Override
            public void message(String channel, String message) {
                String[] split = message.split(";;");
                if (split.length != 3) {
                    Bukkit.getLogger().severe("Invalid message received from RedisEco channel, consider updating RedisEconomy");
                    return;
                }
                if (split[0].equals(RedisEconomyPlugin.getInstance().settings().serverId)) return;
                String accountId = split[1];
                UUID owner = UUID.fromString(split[2]);
                bankOwners.put(accountId, owner);
                if (RedisEconomyPlugin.getInstance().settings().debug) {
                    Bukkit.getLogger().info("01d Received bank owner update " + accountId + " to " + owner);
                }
            }
        });
        connection.async().subscribe(UPDATE_BANK_OWNER_CHANNEL_PREFIX + currencyName);
        if (RedisEconomyPlugin.getInstance().settings().debug) {
            Bukkit.getLogger().info("start1d Listening to RedisEco channel " + UPDATE_BANK_OWNER_CHANNEL_PREFIX + currencyName);
        }
    }

    private void registerBankAccountListener() {
        StatefulRedisPubSubConnection<String, String> connection = currenciesManager.getRedisManager().getPubSubConnection();
        connection.addListener(new RedisCurrencyListener() {
            @Override
            public void message(String channel, String message) {
                String[] split = message.split(";;");
                if (split.length != 3) {
                    Bukkit.getLogger().severe("Invalid message received from RedisEco channel, consider updating RedisEconomy");
                    return;
                }
                if (split[0].equals(RedisEconomyPlugin.getInstance().settings().serverId)) return;
                String accountId = split[1];
                double balance = Double.parseDouble(split[2]);
                updateBankAccountLocal(accountId, balance);
                if (RedisEconomyPlugin.getInstance().settings().debug) {
                    Bukkit.getLogger().info("01c Received bank balance update " + accountId + " to " + balance);
                }
            }
        });
        connection.async().subscribe(UPDATE_BANK_CHANNEL_PREFIX + currencyName);
        if (RedisEconomyPlugin.getInstance().settings().debug) {
            Bukkit.getLogger().info("start1c Listening to RedisEco channel " + UPDATE_BANK_CHANNEL_PREFIX + currencyName);
        }
    }

    @Override
    public boolean hasBankSupport() {
        return true;
    }

    @Override
    public EconomyResponse createBank(@NotNull String accountId, String player) {
        return createBank(accountId, currenciesManager.getUUIDFromUsernameCache(player), "Bank account creation");
    }

    /**
     * Creates a bank account
     *
     * @param accountId   Account ID
     * @param playerOwner Owner UUID
     * @param reason      Reason for the creation
     * @return EconomyResponse with the result of the operation (SUCCESS)
     */
    public EconomyResponse createBank(@NotNull String accountId, UUID playerOwner, String reason) {
        if (bankAccounts.containsKey(accountId))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank account already exists");
        setOwner(accountId, playerOwner);
        updateBankAccount(accountId, 0);
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(playerOwner), 0, currencyName, reason);
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse createBank(@NotNull String accountId, OfflinePlayer player) {
        return createBank(accountId, player.getUniqueId(), "Bank account creation");
    }

    public boolean existsBank(@NotNull String accountId) {
        return bankAccounts.containsKey(accountId);
    }

    @Override
    public EconomyResponse deleteBank(@NotNull String accountId) {
        currenciesManager.getRedisManager().getConnectionPipeline(redisAsyncCommands -> {
            redisAsyncCommands.zrem(BALANCE_BANK_PREFIX + currencyName, accountId);
            return redisAsyncCommands.hdel(BANK_OWNERS.toString(), accountId);
        }).thenAccept(result -> {
            bankAccounts.remove(accountId);
            if (RedisEconomyPlugin.getInstance().settings().debug) {
                Bukkit.getLogger().info("Deleted bank account " + accountId + " with result " + result);
            }
            currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), 0, currencyName, "Bank account deletion");
        });
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankBalance(@NotNull String accountId) {
        return new EconomyResponse(0, bankAccounts.getOrDefault(accountId, 0.0D), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankHas(@NotNull String accountId, double amount) {
        EconomyResponse balanceResponse = bankBalance(accountId);
        if (balanceResponse.balance - amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not enough money");
        }
        return new EconomyResponse(amount, balanceResponse.balance - amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankWithdraw(@NotNull String accountId, double amount) {
        return bankWithdraw(accountId, amount, "Bank withdraw");
    }

    /**
     * Withdraws money from a bank account with a reason
     *
     * @param accountId Account ID
     * @param amount    Amount to withdraw
     * @param reason    Reason for the withdraw
     * @return EconomyResponse with the result of the operation SUCCESS or FAILURE if there is not enough money
     */
    public EconomyResponse bankWithdraw(@NotNull String accountId, double amount, String reason) {
        EconomyResponse hasAmountResponse = bankHas(accountId, amount);
        if (hasAmountResponse.type.equals(EconomyResponse.ResponseType.FAILURE)) {
            return hasAmountResponse;
        }
        updateBankAccount(accountId, hasAmountResponse.balance);//Balance is the new balance with subtracted amount
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), -amount, currencyName, reason);
        return new EconomyResponse(amount, hasAmountResponse.balance, EconomyResponse.ResponseType.SUCCESS, null);

    }

    @Override
    public EconomyResponse bankDeposit(@NotNull String accountId, double amount) {
        return bankDeposit(accountId, amount, "Bank deposit");
    }

    /**
     * Deposits money to a bank account with a reason
     *
     * @param accountId Account ID
     * @param amount    Amount to deposit
     * @param reason    Reason for the deposit
     * @return EconomyResponse with the result of the operation SUCCESS or FAILURE if the bank account does not exist
     */
    public EconomyResponse bankDeposit(@NotNull String accountId, double amount, String reason) {
        if (existsBank(accountId)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank account does not exist");
        }
        EconomyResponse balanceResponse = bankBalance(accountId);
        updateBankAccount(accountId, balanceResponse.balance + amount);//Balance is the new balance with subtracted amount
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), amount, currencyName, reason);
        return new EconomyResponse(amount, balanceResponse.balance + amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, String playerName) {
        if (bankOwners.getOrDefault(accountId, null).equals(currenciesManager.getUUIDFromUsernameCache(playerName))) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not owner");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, OfflinePlayer player) {
        if (bankOwners.getOrDefault(accountId, null).equals(player.getUniqueId())) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not owner");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String accountId, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String accountId, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>(bankAccounts.keySet());
    }

    /**
     * Revert a transaction
     *
     * @param transactionId The transaction id
     * @param transaction   The transaction to revert
     * @return The transaction id that reverted the initial transaction
     */
    @Override
    public CompletionStage<Integer> revertTransaction(int transactionId, @NotNull Transaction transaction) {
        String ownerName = transaction.accountIdentifier.isPlayer() ?//If the sender is a player
                currenciesManager.getUsernameFromUUIDCache(transaction.accountIdentifier.getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.accountIdentifier.toString(); //Else, it's a bank, so we get the bank id
        if (transaction.accountIdentifier.isPlayer()) {//Update player account
            updateAccount(transaction.accountIdentifier.getUUID(), ownerName, getBalance(transaction.accountIdentifier.getUUID()) - transaction.amount);
        } else {//Update bank account
            updateBankAccount(transaction.accountIdentifier.toString(), bankBalance(transaction.accountIdentifier.toString()).balance - transaction.amount);
        }
        if (RedisEconomyPlugin.getInstance().settings().debug) {
            Bukkit.getLogger().info("revert01a reverted on account " + transaction.accountIdentifier + " amount " + transaction.amount);
        }
        return currenciesManager.getExchange().saveTransaction(transaction.accountIdentifier, transaction.receiver, -transaction.amount, currencyName, "Revert #" + transactionId + ": " + transaction.reason);
    }

    private void setOwner(@NotNull String accountId, UUID ownerUUID) {
        currenciesManager.getRedisManager().getConnectionPipeline(connection -> {
            connection.hset(BANK_OWNERS.toString(), accountId, ownerUUID.toString());
            return connection.publish(UPDATE_BANK_OWNER_CHANNEL_PREFIX + currencyName, RedisEconomyPlugin.getInstance().settings().serverId + ";;" + accountId + ";;" + ownerUUID);
        }).thenAccept((result) -> {
            if (RedisEconomyPlugin.getInstance().settings().debug) {
                Bukkit.getLogger().info("Set owner of bank " + accountId + " to " + ownerUUID + " with result " + result);
            }
            bankOwners.put(accountId, ownerUUID);
        });
    }

    void updateBankAccountLocal(String accountId, double balance) {
        bankAccounts.put(accountId, balance);
    }

    private void updateBankAccount(@NotNull String accountId, double balance) {
        updateBankAccountCloudCache(accountId, balance, 0);
        updateBankAccountLocal(accountId, balance);
    }

    private void updateBankAccountCloudCache(@NotNull String accountId, double balance, int tries) {
        try {
            currenciesManager.getRedisManager().getConnectionPipeline(commands -> {
                commands.zadd(BALANCE_BANK_PREFIX + currencyName, balance, accountId);
                return commands.publish(UPDATE_BANK_CHANNEL_PREFIX + currencyName, RedisEconomyPlugin.getInstance().settings().serverId + ";;" + accountId + ";;" + balance).thenAccept((result) -> {
                    if (RedisEconomyPlugin.getInstance().settings().debug) {
                        Bukkit.getLogger().info("01 Sent bank update account " + accountId + " to " + balance);
                    }
                });
            });

        } catch (Exception e) {
            if (tries < 3) {
                e.printStackTrace();
                Bukkit.getLogger().severe("Failed to update bank account " + accountId + " after 3 tries");
                Bukkit.getLogger().severe("Bank accounts are desynchronized");
                updateBankAccountCloudCache(accountId, balance, tries + 1);
            } else {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get ordered accounts from Redis
     * Redis uses ordered sets as data structures
     * This method has a time complexity of O(log(N)+M) with N being the number of elements in the sorted set and M the number of elements returned.
     *
     * @return A list of accounts ordered by balance in Tuples of UUID and balance (UUID is stringified)
     */
    public CompletionStage<List<ScoredValue<String>>> getOrderedBankAccounts() {
        return currenciesManager.getRedisManager().getConnectionAsync(connection ->
                connection.zrevrangeWithScores(BALANCE_PREFIX + currencyName, 0, -1));
    }

    /**
     * Get bank owners from Redis. Use with caution, this method is not cached
     *
     * @return A map of bank owners
     */
    public CompletionStage<Map<String, String>> getRedisBankOwners() {
        return currenciesManager.getRedisManager().getConnectionAsync(connection ->
                connection.hgetall(BANK_OWNERS.toString()));
    }


}
