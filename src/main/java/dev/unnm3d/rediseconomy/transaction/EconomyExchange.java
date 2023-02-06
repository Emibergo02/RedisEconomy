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
import java.util.*;
import java.util.concurrent.CompletionStage;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.NEW_TRANSACTIONS;

@AllArgsConstructor
public class EconomyExchange {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private final CurrenciesManager currenciesManager;


    public CompletionStage<Map<Integer, Transaction>> getTransactions(AccountID accountId) {
        return currenciesManager.getRedisManager().getConnectionAsync(connection ->
                connection.hgetall(NEW_TRANSACTIONS + accountId.toString())
                        .thenApply(this::getTransactionsFromSerialized)
                        .thenApply(integerTransactionMap -> {
                            if(RedisEconomyPlugin.getInstance().settings().debug){
                                integerTransactionMap.forEach((integer, transaction) -> Bukkit.getLogger().info("getTransactions: " + integer + " " + transaction));
                            }
                            return integerTransactionMap;
                        })
                        .exceptionally(exc -> {
                            exc.printStackTrace();
                            return null;
                        })
        );
    }


    public CompletionStage<Transaction> getTransaction(AccountID accountId, int id) {
        return currenciesManager.getRedisManager().getConnectionAsync(connection ->
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
     * @param accountOwner The id of the account, could be a UUID or a bank id (string)
     * @param target       The id of the target account, could be a UUID or a bank id (string)
     * @param amount       The amount of money transferred
     * @param currencyName The name of the currency
     * @param reason       The reason of the transaction
     * @return The transaction id
     */
    public CompletionStage<Integer> saveTransaction(@NotNull AccountID accountOwner, @NotNull AccountID target, double amount, @NotNull String currencyName, @NotNull String reason) {
        long init = System.currentTimeMillis();
        return currenciesManager.getRedisManager().getConnectionAsync(commands -> {

                    Transaction transaction = new Transaction(
                            accountOwner,
                            System.currentTimeMillis(),
                            target, //If target is null, it has been sent from the server
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

    public CompletionStage<Integer> revertTransaction(AccountID accountOwner, int transactionId) {
        return getTransaction(accountOwner, transactionId)
                .thenApply(transaction -> {//get current transaction on Redis
                    if (transaction == null) return -1;
                    Currency currency = currenciesManager.getCurrencyByName(transaction.currencyName);
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
                                    currenciesManager.getRedisManager().getConnectionAsync(connection ->
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

    public void sendTransaction(CommandSender sender, int transactionId, Transaction transaction, String timestampArgument) {
        String accountOwnerName = transaction.accountIdentifier.isPlayer() ?//If the sender is a player
                currenciesManager.getUsernameFromUUIDCache(transaction.accountIdentifier.getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.accountIdentifier.toString(); //Else, it's a bank, so we get the bank id
        String otherAccount = transaction.receiver.isPlayer() ?
                currenciesManager.getUsernameFromUUIDCache(transaction.receiver.getUUID()) :
                transaction.receiver.toString();
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
