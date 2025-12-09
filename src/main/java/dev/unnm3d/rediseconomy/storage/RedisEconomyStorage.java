package dev.unnm3d.rediseconomy.storage;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import io.lettuce.core.ScoredValue;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis implementation of EconomyStorage.
 * Uses Lettuce async connections to retrieve data from Redis.
 */
public class RedisEconomyStorage implements EconomyStorage {

    private final RedisManager redisManager;

    public RedisEconomyStorage(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public CompletionStage<List<ScoredValue<String>>> getOrderedAccounts(String currencyName, int limit) {
        return redisManager.getConnectionAsync(accounts ->
                accounts.zrevrangeWithScores(RedisKeys.BALANCE_PREFIX + currencyName, 0, limit));
    }

    @Override
    public CompletionStage<Double> getAccountBalance(String currencyName, UUID uuid) {
        return redisManager.getConnectionAsync(connection ->
                connection.zscore(RedisKeys.BALANCE_PREFIX + currencyName, uuid.toString()));
    }

    @Override
    public CompletionStage<Map<UUID, Double>> getPlayerMaxBalances(String currencyName) {
        return redisManager.getConnectionAsync(accounts ->
                        accounts.hgetall(RedisKeys.MAX_PLAYER_BALANCES + currencyName))
                .thenApply(result -> {
                    final Map<UUID, Double> maxBalances = new HashMap<>();
                    result.forEach((key, value) -> maxBalances.put(UUID.fromString(key), Double.parseDouble(value)));
                    return maxBalances;
                });
    }

    @Override
    public CompletionStage<List<ScoredValue<String>>> getOrderedBankAccounts(String currencyName) {
        return redisManager.getConnectionAsync(connection ->
                connection.zrevrangeWithScores(RedisKeys.BALANCE_PREFIX + currencyName, 0, -1));
    }

    @Override
    public CompletionStage<Map<String, String>> getBankOwners() {
        return redisManager.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.BANK_OWNERS.toString()));
    }

    @Override
    public CompletionStage<ConcurrentHashMap<String, UUID>> loadNameUniqueIds() {
        return redisManager.getConnectionAsync(connection ->
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
        return redisManager.getConnectionAsync(connection ->
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
        return redisManager.getConnectionAsync(connection ->
                connection.hgetall(RedisKeys.TRANSACTIONS + accountId.toString()));
    }

    @Override
    public CompletionStage<String> getCurrentTransactionCounter() {
        return redisManager.getConnectionAsync(connection ->
                connection.get(RedisKeys.TRANSACTIONS_COUNTER.toString()));
    }
}
