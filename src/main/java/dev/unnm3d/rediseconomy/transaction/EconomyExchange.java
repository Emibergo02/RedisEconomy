package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.api.TransactionEvent;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScriptOutputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.NEW_TRANSACTIONS;
import static dev.unnm3d.rediseconomy.redis.RedisKeys.TRANSACTIONS_COUNTER;

public class EconomyExchange {

    private final RedisEconomyPlugin plugin;
    private final ExecutorService executorService;

    /**
     * Constructor for EconomyExchange
     *
     * @param plugin The RedisEconomyPlugin instance
     */
    public EconomyExchange(final RedisEconomyPlugin plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newFixedThreadPool(plugin.getConfigManager().getSettings().transactionExecutorThreads);
    }

    /**
     * Get transactions from an account id
     *
     * @param accountId Account id
     * @param limit     Maximum number of transactions to return
     * @return Map of transaction ids and transactions
     */
    public CompletionStage<Map<Long, Transaction>> getTransactions(AccountID accountId, int limit) {
        return CompletableFuture.supplyAsync(() ->
                        plugin.getCurrenciesManager().getRedisManager().getConnectionSync(connection ->
                                connection.hgetall(NEW_TRANSACTIONS + accountId.toString())
                        ), executorService)
                .thenApply(transactions -> {
                    if (transactions == null || transactions.isEmpty()) {
                        return new HashMap<Long, Transaction>();
                    }

                    final Map<Long, Transaction> transactionsMap = new HashMap<>();
                    transactions.entrySet().stream()
                            .sorted(Comparator.<Map.Entry<String, String>>comparingInt(entry ->
                                    Integer.parseInt(entry.getKey())).reversed())
                            .limit(limit)
                            .forEach(entry -> transactionsMap.put(
                                    Long.parseLong(entry.getKey()),
                                    Transaction.fromString(entry.getValue())
                            ));
                    return transactionsMap;
                })
                .exceptionally(exc -> {
                    exc.printStackTrace();
                    return new HashMap<Long, Transaction>(); // Return empty map instead of null for better error handling
                });
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
                        if (keys.isEmpty()) {
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
    public CompletionStage<Transaction> getTransaction(@NotNull AccountID accountId, long id) {
        return CompletableFuture.supplyAsync(() -> {
            return Transaction.fromString(plugin.getCurrenciesManager().getRedisManager().getConnectionSync(connection ->
                    connection.hget(NEW_TRANSACTIONS + accountId.toString(), String.valueOf(id))));
        }, executorService).orTimeout(plugin.getConfigManager().getSettings().redis.timeout(), TimeUnit.MILLISECONDS).exceptionally(exc -> {
            exc.printStackTrace();
            return null;
        });
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
    public CompletionStage<List<Long>> savePaymentTransaction(@NotNull UUID sender, @NotNull UUID target, double amount, @NotNull Currency currency, @NotNull String reason) {
        if (!currency.shouldSaveTransactions()) return CompletableFuture.completedStage(List.of((long) -1, (long) -1));

        long init = System.currentTimeMillis();
        final String stackTrace = getCallerPluginString();
        return CompletableFuture.supplyAsync(() -> {
                    TransactionEvent transactionSenderEvent = new TransactionEvent(new Transaction(
                            new AccountID(sender),
                            System.currentTimeMillis(),
                            new AccountID(target),
                            -amount,
                            currency.getCurrencyName(),
                            reason + stackTrace,
                            null));
                    TransactionEvent transactionReceiverEvent = new TransactionEvent(new Transaction(
                            new AccountID(target),
                            System.currentTimeMillis(),
                            new AccountID(sender),
                            amount,
                            currency.getCurrencyName(),
                            reason + stackTrace,
                            null));

                    plugin.getScheduler().runTask(() -> {
                        plugin.getServer().getPluginManager().callEvent(transactionSenderEvent);
                        plugin.getServer().getPluginManager().callEvent(transactionReceiverEvent);
                    });

                    //noinspection unchecked
                    return plugin.getCurrenciesManager().getRedisManager().getConnectionSync(connection ->
                            (List<Long>) connection.eval(
                                    "local a=redis.call('incr',KEYS[1])" +
                                            "local b=redis.call('incr',KEYS[1])" +
                                            "redis.call('hset',KEYS[2],a,ARGV[1])" +
                                            "redis.call('hset',KEYS[3],b,ARGV[2])" +
                                            "if tonumber(ARGV[3])>0 then redis.call('hexpire',KEYS[2],ARGV[3],'FIELDS',1,a)redis.call('hexpire',KEYS[3],ARGV[3],'FIELDS',1,b)end" +
                                            " return{a,b}",
                                    ScriptOutputType.MULTI,
                                    new String[]{
                                            TRANSACTIONS_COUNTER.toString(),
                                            NEW_TRANSACTIONS + sender.toString(),
                                            NEW_TRANSACTIONS + target.toString()}, //Key rediseco:transactions:playerUUID
                                    transactionSenderEvent.getTransaction().toString(),
                                    transactionReceiverEvent.getTransaction().toString(),
                                    String.valueOf(currency.getTransactionsTTL())));
                }, executorService).orTimeout(plugin.getConfigManager().getSettings().redis.timeout(), TimeUnit.MILLISECONDS)
                .exceptionally(exc -> {
                    RedisEconomyPlugin.debug("ERROR!!!!! 03payment Exception while saving transaction for " + sender + " and " + target + ": " + exc.getMessage());
                    return List.of((long) -1, (long) -1);
                })
                .thenApply(response -> {
                    RedisEconomyPlugin.debug("03payment Transaction for " + sender + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response.get(0) + " !");
                    RedisEconomyPlugin.debug("03payment Transaction for " + target + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response.get(1) + " !");

                    return response;
                });
    }


    /**
     * Saves a transaction
     *
     * @param accountOwner The id of the account, could be a UUID or a bank id (string)
     * @param target       The id of the target account, could be a UUID or a bank id (string)
     * @param amount       The amount of money transferred
     * @param currency     The currency of the transaction
     * @param reason       The reason of the transaction
     * @return The transaction id
     */
    public CompletionStage<Long> saveTransaction(@NotNull AccountID accountOwner, @NotNull AccountID target, double amount, @NotNull Currency currency, @NotNull String reason) {
        if (!currency.shouldSaveTransactions()) return CompletableFuture.completedStage((long) -1);
        long init = System.currentTimeMillis();

        final String stackTrace = getCallerPluginString();

        return CompletableFuture.supplyAsync(() -> {
                    TransactionEvent transactionEvent = new TransactionEvent(new Transaction(
                            accountOwner,
                            System.currentTimeMillis(),
                            target, //If target is null, it has been sent from the server
                            amount, currency.getCurrencyName(), reason + stackTrace, null));
                    plugin.getScheduler().runTask(() -> plugin.getServer().getPluginManager().callEvent(transactionEvent));
                    return (long) plugin.getCurrenciesManager().getRedisManager().getConnectionSync(commands -> commands.eval(
                            "local a=redis.call('incr',KEYS[1])" +
                                    "redis.call('hset',KEYS[2],a,ARGV[1])" +
                                    "if tonumber(ARGV[2])>0 then redis.call('hexpire',KEYS[2],ARGV[2],'FIELDS',1,a)end" +
                                    " return a",
                            ScriptOutputType.INTEGER,
                            new String[]{
                                    TRANSACTIONS_COUNTER.toString(),
                                    NEW_TRANSACTIONS + accountOwner.toString() //Key rediseco:transactions:playerUUID
                            },
                            transactionEvent.getTransaction().toString(),
                            String.valueOf(currency.getTransactionsTTL())));
                }, executorService).orTimeout(plugin.getConfigManager().getSettings().redis.timeout(), TimeUnit.MILLISECONDS)
                .exceptionally(exc -> {
                    exc.printStackTrace();
                    RedisEconomyPlugin.debug("ERROR!!!!! 03 Transaction Exception while saving transaction for " + accountOwner + " and " + target + ": " + exc.getMessage());
                    return (long) -1;
                })
                .thenApply(response -> {
                    RedisEconomyPlugin.debug("03 Transaction for " + accountOwner + " saved in " + (System.currentTimeMillis() - init) + " ms with id " + response + " !");
                    return response;
                });
    }

    /**
     * Revert a transaction creating a new transaction with the opposite amount
     *
     * @param accountOwner  The id of the account
     * @param transactionId The id of the transaction to revert
     * @return The id of the new transaction that reverts the old one or the id of the already existing transaction that reverts the old one
     */
    public CompletionStage<Long> revertTransaction(AccountID accountOwner, long transactionId) {
        return getTransaction(accountOwner, transactionId)
                .thenCompose(transaction -> {
                    // Early return for null transaction
                    if (transaction == null) {
                        return CompletableFuture.completedFuture((long) -1);
                    }

                    // Get currency - this should be fast since it's likely cached
                    Currency currency = plugin.getCurrenciesManager().getCurrencyByName(transaction.getCurrencyName());
                    if (currency == null) {
                        return CompletableFuture.completedFuture((long) -1);
                    }

                    // Check if already reverted
                    if (transaction.getRevertedWith() != null) {
                        RedisEconomyPlugin.debug("revert01b Transaction " + transactionId +
                                " already reverted with " + transaction.getRevertedWith());
                        return CompletableFuture.completedFuture(Long.valueOf(transaction.getRevertedWith()));
                    }

                    // Fire event asynchronously (non-blocking)
                    final TransactionEvent revertTransactionEvent = new TransactionEvent(transaction);
                    plugin.getScheduler().runTask(() ->
                            plugin.getServer().getPluginManager().callEvent(revertTransactionEvent));

                    // Proceed with revert operation
                    return currency.revertTransaction(transactionId, revertTransactionEvent.getTransaction())
                            .thenCompose(newId -> {
                                if (newId == null) {
                                    return CompletableFuture.completedFuture(newId);
                                }

                                // Update the transaction with revert info
                                revertTransactionEvent.getTransaction().setRevertedWith(String.valueOf(newId));

                                // Update Redis asynchronously and return the newId
                                return plugin.getCurrenciesManager().getRedisManager()
                                        .getConnectionAsync(connection ->
                                                connection.hset(
                                                        NEW_TRANSACTIONS + accountOwner.toString(),
                                                        String.valueOf(transactionId),
                                                        revertTransactionEvent.getTransaction().toString()
                                                )
                                        )
                                        .thenApply(updateResult -> {
                                            RedisEconomyPlugin.debug("revert02 Replace transaction " + transactionId +
                                                    " with a new revertedWith id on Redis: " + updateResult);
                                            return newId; // Return the new transaction ID, not the Redis update result
                                        })
                                        .exceptionally(throwable -> {
                                            RedisEconomyPlugin.debug("Failed to update transaction in Redis: " + throwable.getMessage());
                                            return newId; // Still return newId even if Redis update fails
                                        });
                            });
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
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .skip(3)
                .filter(s -> plugin.settings().callBlacklistRegex.stream().noneMatch(blRegex -> s.getClassName().matches(blRegex)))
                .findFirst()
                .map(ste -> "\nCall: " + ste.getClassName() + ":" + ste.getMethodName() + ":" + ste.getLineNumber())
                .orElse("");
    }

}
