package dev.unnm3d.rediseconomy.storage;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScoredValue;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis implementation of EconomyStorage.
 * Uses Lettuce async connections to retrieve data from Redis.
 * Extends RedisManager to have direct access to all Redis operations.
 */
public class RedisEconomyStorage extends RedisManager implements EconomyStorage {

    public RedisEconomyStorage(RedisClient lettuceRedisClient, int poolSize) {
        super(lettuceRedisClient, poolSize);
    }

    @Override
    public CompletionStage<List<ScoredValue<String>>> getOrderedAccounts(String currencyName, int limit) {
        return this.getConnectionAsync(accounts ->
                accounts.zrevrangeWithScores(RedisKeys.BALANCE_PREFIX + currencyName, 0, limit));
    }

    @Override
    public CompletionStage<Double> getAccountBalance(String currencyName, UUID uuid) {
        return this.getConnectionAsync(connection ->
                connection.zscore(RedisKeys.BALANCE_PREFIX + currencyName, uuid.toString()));
    }

    @Override
    public CompletionStage<Map<UUID, Double>> getPlayerMaxBalances(String currencyName) {
        return this.getConnectionAsync(accounts ->
                        accounts.hgetall(RedisKeys.MAX_PLAYER_BALANCES + currencyName))
                .thenApply(result -> {
                    final Map<UUID, Double> maxBalances = new HashMap<>();
                    result.forEach((key, value) -> maxBalances.put(UUID.fromString(key), Double.parseDouble(value)));
                    return maxBalances;
                });
    }

    @Override
    public CompletionStage<List<ScoredValue<String>>> getOrderedBankAccounts(String currencyName) {
        return this.getConnectionAsync(connection ->
                connection.zrevrangeWithScores(RedisKeys.BALANCE_PREFIX + currencyName, 0, -1));
    }

    @Override
    public CompletionStage<Map<String, String>> getBankOwners() {
        return this.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.BANK_OWNERS.toString()));
    }

    @Override
    public CompletionStage<ConcurrentHashMap<String, UUID>> loadNameUniqueIds() {
        return this.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.NAME_UUID.toString())
                        .thenApply(result -> {
                            ConcurrentHashMap<String, UUID> nameUUIDs = new ConcurrentHashMap<>();
                            result.forEach((name, uuid) -> nameUUIDs.put(name, UUID.fromString(uuid)));
                            RedisEconomyPlugin.debug("start0 Loaded " + nameUUIDs.size() + " name-uuid pairs");
                            return nameUUIDs;
                        })
        );
    }

    @Override
    public CompletionStage<ConcurrentHashMap<UUID, List<UUID>>> loadLockedAccounts() {
        return this.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.LOCKED_ACCOUNTS.toString())
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

    @Override
    public CompletionStage<Map<String, String>> getTransactions(AccountID accountId) {
        return this.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.TRANSACTIONS + accountId.toString()));
    }

    @Override
    public CompletionStage<String> getCurrentTransactionCounter() {
        return this.getConnectionAsync(connection ->
                connection.get(RedisKeys.TRANSACTIONS_COUNTER.toString()));
    }

    // Write operations

    @Override
    public Optional<List<Object>> updateAccount(String currencyName, UUID uuid, String playerName, double balance) {
        return this.executeTransaction(reactiveCommands -> {
            reactiveCommands.zadd(RedisKeys.BALANCE_PREFIX + currencyName, balance, uuid.toString());
            if (playerName != null)
                reactiveCommands.hset(RedisKeys.NAME_UUID.toString(), playerName, uuid.toString());
            reactiveCommands.publish(RedisKeys.UPDATE_PLAYER_CHANNEL_PREFIX + currencyName,
                    RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + uuid + ";;" + playerName + ";;" + balance);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<Object>> updateBulkAccounts(String currencyName, List<ScoredValue<String>> balances, Map<String, String> nameUUIDs) {
        Optional<List<Object>> result = this.executeTransaction(commands -> {
            ScoredValue<String>[] balancesArray = new ScoredValue[balances.size()];
            balances.toArray(balancesArray);

            commands.zadd(RedisKeys.BALANCE_PREFIX + currencyName, balancesArray);
            commands.hset(RedisKeys.NAME_UUID.toString(), nameUUIDs);
        });
        
        result.ifPresent(r -> {
            Bukkit.getLogger().info("migration01 updated balances into " + RedisKeys.BALANCE_PREFIX + currencyName + " accounts. result " + r.get(0));
            Bukkit.getLogger().info("migration02 updated nameuuids into " + RedisKeys.NAME_UUID + " accounts. result " + r.get(1));
        });
        
        return result;
    }

    @Override
    public void updatePlayerMaxBalance(String currencyName, UUID uuid, double amount, double defaultMax) {
        this.getConnectionPipeline(asyncCommands -> {
            if (amount == defaultMax) {
                asyncCommands.hdel(RedisKeys.MAX_PLAYER_BALANCES + currencyName, uuid.toString());
            } else {
                asyncCommands.hset(RedisKeys.MAX_PLAYER_BALANCES + currencyName, uuid.toString(), String.valueOf(amount));
            }
            return asyncCommands.publish(RedisKeys.UPDATE_MAX_BAL_PREFIX + currencyName, RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + uuid + ";;" + amount);
        });
    }

    @Override
    public void removeNameUniqueIds(Map<String, UUID> toRemove) {
        String[] toRemoveArray = toRemove.keySet().toArray(new String[0]);
        for (int i = 0; i < toRemoveArray.length; i++) {
            if (toRemoveArray[i] == null) {
                toRemoveArray[i] = "null";
            }
        }
        this.getConnectionAsync(connection ->
                connection.hdel(RedisKeys.NAME_UUID.toString(), toRemoveArray).thenAccept(integer ->
                        RedisEconomyPlugin.debug("purge0 Removed " + integer + " name-uuid pairs")));
    }

    @Override
    public void switchCurrency(String oldCurrencyName, String newCurrencyName) {
        this.getConnectionPipeline(asyncCommands -> {
            asyncCommands.copy(RedisKeys.BALANCE_PREFIX + oldCurrencyName,
                            RedisKeys.BALANCE_PREFIX + oldCurrencyName + "_backup")
                    .thenAccept(success -> RedisEconomyPlugin.debug("Switch0 - Backup currency accounts: " + success));

            asyncCommands.rename(RedisKeys.BALANCE_PREFIX + newCurrencyName,
                    RedisKeys.BALANCE_PREFIX + oldCurrencyName).thenAccept(success ->
                    RedisEconomyPlugin.debug("Switch1 - Overwrite new currency key with the old one: " + success));

            asyncCommands.renamenx(RedisKeys.BALANCE_PREFIX + oldCurrencyName + "_backup",
                    RedisKeys.BALANCE_PREFIX + newCurrencyName).thenAccept(success ->
                    RedisEconomyPlugin.debug("Switch2 - Write the backup on the new currency key: " + success));
            return null;
        });
    }

    @Override
    public CompletionStage<Long> updateLockedAccounts(UUID uuid, String redisString) {
        return this.getConnectionPipeline(connection -> {
            connection.hset(RedisKeys.LOCKED_ACCOUNTS.toString(),
                    uuid.toString(),
                    redisString
            );
            return connection.publish(RedisKeys.UPDATE_LOCKED_ACCOUNTS.toString(), uuid + "," + redisString);
        });
    }

    // Bank operations

    @Override
    public CompletionStage<Long> deleteBank(String currencyName, String accountId) {
        return this.getConnectionPipeline(redisAsyncCommands -> {
            redisAsyncCommands.zrem(RedisKeys.BALANCE_BANK_PREFIX + currencyName, accountId);
            return redisAsyncCommands.hdel(RedisKeys.BANK_OWNERS.toString(), accountId);
        });
    }

    @Override
    public CompletionStage<Long> setBankOwner(String currencyName, String accountId, UUID ownerUUID) {
        return this.getConnectionPipeline(connection -> {
            connection.hset(RedisKeys.BANK_OWNERS.toString(), accountId, ownerUUID.toString());
            return connection.publish(RedisKeys.UPDATE_BANK_OWNER_CHANNEL_PREFIX + currencyName, 
                    RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + accountId + ";;" + ownerUUID);
        });
    }

    @Override
    public Optional<List<Object>> updateBankAccount(String currencyName, String accountId, double balance) {
        return this.executeTransaction(reactiveCommands -> {
            reactiveCommands.zadd(RedisKeys.BALANCE_BANK_PREFIX + currencyName, balance, accountId);
            reactiveCommands.publish(RedisKeys.UPDATE_BANK_CHANNEL_PREFIX + currencyName,
                    RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + accountId + ";;" + balance);
        });
    }
}

