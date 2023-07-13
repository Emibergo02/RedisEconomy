package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScriptOutputType;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.NEW_TRANSACTIONS;

@AllArgsConstructor
public class EconomyExchange {

    private final RedisEconomyPlugin plugin;

    /**
     * Get transactions from an account id
     *
     * @param accountId Account id
     * @return Map of transaction ids and transactions
     */
    public CompletionStage<Map<Integer, Transaction>> getTransactions(AccountID accountId) {
        return plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection ->
                connection.hgetall(NEW_TRANSACTIONS + accountId.toString())
                        .thenApply(this::getTransactionsFromSerialized)
                        .exceptionally(exc -> {
                            exc.printStackTrace();
                            return null;
                        })
        );
    }

    /**
     * Remove all transactions from Redis
     *
     * @return How many transaction accounts were removed
     */
    public CompletionStage<Long> removeAllTransactions() {
        return plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection -> {
                    try {
                        List<String> keys = connection.keys(NEW_TRANSACTIONS + "*").get();
                        if (keys.size() == 0) {
                            return CompletableFuture.completedFuture(0L);
                        }
                        return connection.del(keys.toArray(new String[0]));
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Get transactions from an account id and transaction id
     *
     * @param accountId Account id
     * @param id        Transaction id
     * @return Transaction
     */
    public CompletionStage<Transaction> getTransaction(AccountID accountId, int id) {
        return plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection ->
                connection.hget(NEW_TRANSACTIONS + accountId.toString(), String.valueOf(id))
                        .thenApply(Transaction::fromString)
                        .exceptionally(exc -> {
                            exc.printStackTrace();
                            return null;
                        }));
    }

    /**
     * Save payment transaction
     *
     * @param sender   Sender of the transaction
     * @param target   Target of the transaction
     * @param amount   Amount of the transaction
     * @param currency Currency of the transaction
     * @param reason   Reason of the transaction
     * @return List of ids: the first one is the id of the transaction on the sender side, the second one is the id of the transaction on the target side
     */
    public CompletionStage<List<Integer>> savePaymentTransaction(@NotNull UUID sender, @NotNull UUID target, double amount, @NotNull Currency currency, @NotNull String reason) {
        long init = System.currentTimeMillis();
        reason += getCallerPluginString();

        Transaction transactionSender = new Transaction(
                new AccountID(sender),
                System.currentTimeMillis(),
                new AccountID(target),
                -amount,
                currency.getCurrencyName(),
                reason,
                null);
        Transaction transactionReceiver = new Transaction(
                new AccountID(target),
                System.currentTimeMillis(),
                new AccountID(sender),
                amount,
                currency.getCurrencyName(),
                reason,
                null);

        return plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection ->
                        connection.<List<Integer>>eval(
                                "local senderCurrentId=redis.call('hlen', KEYS[1]);" +
                                        "local receiverCurrentId=redis.call('hlen', KEYS[2]);" +
                                        "redis.call('hset', KEYS[1], senderCurrentId, ARGV[1]);" +
                                        "redis.call('hset', KEYS[2], receiverCurrentId, ARGV[2]);" +
                                        "return {senderCurrentId,receiverCurrentId};", //Return the id of the new transaction
                                ScriptOutputType.MULTI,
                                new String[]{
                                        NEW_TRANSACTIONS + sender.toString(),
                                        NEW_TRANSACTIONS + target.toString()}, //Key rediseco:transactions:playerUUID
                                transactionSender.toString(),
                                transactionReceiver.toString()))
                .thenApply(response -> {
                    if (RedisEconomyPlugin.getInstance().settings().debug) {
                        Bukkit.getLogger().info("03payment Transaction for " + sender + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response.get(0) + " !");
                        Bukkit.getLogger().info("03payment Transaction for " + target + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response.get(1) + " !");
                    }
                    return response;
                }).exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }


    /**
     * Saves a transaction
     *
     * @param accountOwner The id of the account, could be a UUID or a bank id (string)
     * @param target       The id of the target account, could be a UUID or a bank id (string)
     * @param amount       The amount of money transferred
     * @param currencyName The name of the currency
     * @param reason       The reason of the transaction
     * @return The transaction id
     */
    public CompletionStage<Integer> saveTransaction(@NotNull AccountID accountOwner, @NotNull AccountID target, double amount, @NotNull String currencyName, @NotNull String reason) {
        long init = System.currentTimeMillis();
        return plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(commands -> {

                    Transaction transaction = new Transaction(
                            accountOwner,
                            System.currentTimeMillis(),
                            target, //If target is null, it has been sent from the server
                            amount, currencyName, reason + getCallerPluginString(), null);

                    return commands.eval(
                            "local currentId=redis.call('hlen', KEYS[1]);" + //Get the current size of the hash
                                    "redis.call('hset', KEYS[1], currentId, ARGV[1]);" + //Add the new transaction
                                    "return currentId;", //Return the id of the new transaction
                            ScriptOutputType.INTEGER,
                            new String[]{NEW_TRANSACTIONS + accountOwner.toString()}, //Key rediseco:transactions:playerUUID
                            transaction.toString()).thenApply(response -> {
                        if (RedisEconomyPlugin.getInstance().settings().debug) {
                            Bukkit.getLogger().info("03 Transaction for " + accountOwner + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response + " !");
                        }
                        return ((Long) response).intValue();
                    }).exceptionally(throwable -> {
                        throwable.printStackTrace();
                        return null;
                    });
                }
        );
    }

    /**
     * Revert a transaction creating a new transaction with the opposite amount
     *
     * @param accountOwner  The id of the account
     * @param transactionId The id of the transaction to revert
     * @return The id of the new transaction that reverts the old one or the id of the already existing transaction that reverts the old one
     */
    public CompletionStage<Integer> revertTransaction(AccountID accountOwner, int transactionId) {
        return getTransaction(accountOwner, transactionId)
                .thenApply(transaction -> {//get current transaction on Redis
                    if (transaction == null) return -1;
                    Currency currency = plugin.getCurrenciesManager().getCurrencyByName(transaction.currencyName);
                    if (currency == null) {
                        return -1;
                    }
                    if (transaction.revertedWith != null) {
                        //already cancelled
                        if (RedisEconomyPlugin.getInstance().settings().debug) {
                            Bukkit.getLogger().info("revert01b Transaction " + transactionId + " already reverted with " + transaction.revertedWith);
                        }
                        return Integer.valueOf(transaction.revertedWith);
                    }

                    return currency.revertTransaction(transactionId, transaction)
                            .thenApply(newId -> {
                                if (newId != null) {
                                    transaction.revertedWith = String.valueOf(newId);
                                    //replace transaction on Redis
                                    plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection ->
                                            connection.hset(NEW_TRANSACTIONS + accountOwner.toString(), //Key rediseco:transactions:playerUUID
                                                            String.valueOf(transactionId), //Previous transaction id
                                                            transaction.toString())
                                                    .thenApply(result2 -> {
                                                        if (RedisEconomyPlugin.getInstance().settings().debug) {
                                                            Bukkit.getLogger().info("revert02 Replace transaction " + transactionId + " with a new revertedWith id on Redis: " + result2);
                                                        }
                                                        return result2;
                                                    }));

                                }
                                return newId;
                            }).toCompletableFuture().join();
                });
    }


    /**
     * Deserializes a string into an array of transactions
     *
     * @param serialized The serialized transactions
     * @return The deserialized transactions
     */
    private @NotNull Map<Integer, Transaction> getTransactionsFromSerialized(@Nullable Map<String, String> serialized) {
        if (serialized == null)
            return new HashMap<>();
        Map<Integer, Transaction> transactions = new HashMap<>();
        serialized.forEach((k, v) -> transactions.put(Integer.parseInt(k), Transaction.fromString(v)));
        return transactions;
    }

    public String getCallerPluginString() {
        if (!plugin.settings().registerCalls) return "";
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stackTraceElements.length; i++) {
            if (!stackTraceElements[i].getClassName().startsWith("org.bukkit") &&
                    !stackTraceElements[i].getClassName().startsWith("dev.unnm3d.rediseconomy") &&
                    !stackTraceElements[i].getClassName().startsWith("com.mojang")
            ) {
                return "\nCall: " + stackTraceElements[i].getClassName() + ":" + stackTraceElements[i].getMethodName();
            }
        }
        return "";
    }

}
