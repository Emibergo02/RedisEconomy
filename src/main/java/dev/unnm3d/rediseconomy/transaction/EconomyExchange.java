package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.NEW_TRANSACTIONS;

@AllArgsConstructor
public class EconomyExchange {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private final CurrenciesManager currenciesManager;

    public CompletableFuture<Map<Integer, Transaction>> getTransactions(UUID player) {
        return currenciesManager.getRedisManager().getConnection(connection -> {
            connection.setTimeout(Duration.ofMillis(1000));
            return connection.async().hgetall(NEW_TRANSACTIONS + player.toString()).thenApply(this::getTransactionsFromSerialized).exceptionally(exc -> {
                exc.printStackTrace();
                return null;
            }).toCompletableFuture();
        });
    }

    public CompletableFuture<Transaction> getTransaction(UUID player, int id) {
        return currenciesManager.getRedisManager().getConnection(connection -> {
            connection.setTimeout(Duration.ofMillis(1000));
            return connection.async().hget(NEW_TRANSACTIONS + player.toString(), String.valueOf(id)).thenApply(Transaction::fromString).exceptionally(exc -> {
                exc.printStackTrace();
                return null;
            }).toCompletableFuture();
        });
    }

    /**
     * Save payment transaction
     *
     * @param client   Redis client async commands
     * @param sender   Sender of the transaction
     * @param target   Target of the transaction
     * @param amount   Amount of the transaction
     * @param currency Currency of the transaction
     * @param reason   Reason of the transaction
     * @return List of ids: the first one is the id of the transaction on the sender side, the second one is the id of the transaction on the target side
     */
    public CompletionStage<List<Integer>> savePaymentTransaction(@NotNull RedisAsyncCommands<String, String> client, @NotNull UUID sender, @NotNull UUID target, double amount, @NotNull Currency currency, @NotNull String reason) {
        long init = System.currentTimeMillis();

        Transaction transactionSender = new Transaction(
                sender,
                System.currentTimeMillis(),
                target,
                -amount,
                currency.getCurrencyName(),
                reason,
                null);
        Transaction transactionReceiver = new Transaction(
                target,
                System.currentTimeMillis(),
                sender,
                amount,
                currency.getCurrencyName(),
                reason,
                null);

        RedisFuture<List<Integer>> evalResult = client.eval(
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
                transactionReceiver.toString());
        return evalResult.thenApply(response -> {
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
     * @param accountOwner The owner of the account
     * @param target       Who transferred the money to the account owner. If it is the server the uuid will be UUID.fromString("00000000-0000-0000-0000-000000000000")
     * @param amount       The amount of money transferred
     * @param currencyName The name of the currency
     * @param reason       The reason of the transaction
     * @return The transaction id
     */
    public CompletionStage<Integer> saveTransaction(UUID accountOwner, UUID target, double amount, String currencyName, String reason) {
        long init = System.currentTimeMillis();
        return currenciesManager.getRedisManager().getConnection(connection -> {                                               //Get connection
                    RedisAsyncCommands<String, String> commands = connection.async();

                    Transaction transaction = new Transaction(accountOwner, System.currentTimeMillis(),
                            target == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : target, //If target is null, it has been sent from the server
                            amount, currencyName, reason, null);

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

    public CompletionStage<Integer> revertTransaction(UUID accountOwner, int transactionId) {
        return getTransaction(accountOwner, transactionId).thenApply(transaction -> {//get current transaction on Redis
            if (transaction == null) return null;
            Currency currency = currenciesManager.getCurrencyByName(transaction.currencyName);
            if (currency == null) {
                return null;
            }
            if (transaction.revertedWith != null) {
                //already cancelled
                if (RedisEconomyPlugin.getInstance().settings().debug) {
                    Bukkit.getLogger().info("revert01b Transaction " + transactionId + " already reverted with " + transaction.revertedWith);
                }
                return Integer.valueOf(transaction.revertedWith);
            }

            return currency.revertTransaction(transactionId, transaction).thenApply(newId -> {
                if (newId != null) {
                    transaction.revertedWith = String.valueOf(newId);
                    //replace transaction on Redis
                    boolean result = currenciesManager.getRedisManager().getConnection(connection ->
                            connection.sync().hset(
                                    NEW_TRANSACTIONS + accountOwner.toString(), //Key rediseco:transactions:playerUUID
                                    String.valueOf(transactionId), //Previous transaction id
                                    transaction.toString())); //New replaced transaction
                    if (RedisEconomyPlugin.getInstance().settings().debug) {
                        Bukkit.getLogger().info("revert02 Replace transaction " + transactionId + " with a new revertedWith id on Redis: " + result);
                    }
                }
                return newId;
            }).join();
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

    public void sendTransaction(CommandSender sender, int transactionId, Transaction transaction, String timestampArgument) {
        String accountOwnerName = currenciesManager.getUsernameFromUUIDCache(transaction.sender);
        String otherAccount = transaction.receiver.equals(UUID.fromString("00000000-0000-0000-0000-000000000000")) ? "Server" : currenciesManager.getUsernameFromUUIDCache(transaction.receiver);
        Currency currency = currenciesManager.getCurrencyByName(transaction.currencyName);

        String transactionMessage = RedisEconomyPlugin.getInstance().langs().transactionItem.incomingFunds();
        if (transaction.amount < 0) {
            transactionMessage = RedisEconomyPlugin.getInstance().langs().transactionItem.outgoingFunds();
        }
        transactionMessage = transactionMessage
                .replace("%id%", transactionId + "")
                .replace("%amount%", String.valueOf(transaction.amount))
                .replace("%symbol%", currency == null ? "" : currency.getCurrencyPlural())
                .replace("%account-owner%", accountOwnerName == null ? "Unknown" : accountOwnerName)
                .replace("%other-account%", otherAccount == null ? "Unknown" : otherAccount)
                .replace("%timestamp%", convertTimeWithLocalTimeZome(transaction.timestamp))
                .replace("%reason%", transaction.reason);
        if (timestampArgument != null)
            transactionMessage = transactionMessage.replace("%afterbefore%", timestampArgument);
        RedisEconomyPlugin.getInstance().langs().send(sender, transactionMessage);
    }

    public void sendTransaction(CommandSender sender, int transactionId, Transaction transaction) {
        sendTransaction(sender, transactionId, transaction, null);
    }

    public String convertTimeWithLocalTimeZome(long time) {
        Date date = new Date(time);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }

    public Date formatDate(String fromString) throws ParseException {
        return dateFormat.parse(fromString);
    }


}
