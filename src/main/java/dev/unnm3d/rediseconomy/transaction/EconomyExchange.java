package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.TRANSACTIONS;

@AllArgsConstructor
public class EconomyExchange {

    private final CurrenciesManager currenciesManager;

    public CompletableFuture<Transaction[]> getTransactions(UUID player) {
        return currenciesManager.getRedisManager().getConnection(connection -> {
            connection.setTimeout(Duration.ofMillis(1000));
            return connection.async().hget(TRANSACTIONS.toString(), player.toString()).thenApply(this::getTransactionsFromSerialized).toCompletableFuture();
        });
    }

    public void savePaymentTransaction(RedisAsyncCommands<String, String> client, UUID sender, UUID target, double amount) {
        long init = System.currentTimeMillis();
        client.hmget(TRANSACTIONS.toString(), sender.toString(), target.toString()).thenApply(lista -> {
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("03 Retrieve transactions from redis... next 03b");
            }
            //Add the new transaction
            String senderTransactionsSerialized = updateTransactionFromSerialized(
                    lista.get(0).isEmpty() ? null : lista.get(0).getValue(),
                    sender, target, -amount,
                    "vault",
                    "Payment");
            String receiverTransactionsSerialized = updateTransactionFromSerialized(
                    lista.get(1).isEmpty() ? null : lista.get(1).getValue(),
                    sender, target, amount,
                    "vault",
                    "Payment");

            Map<String, String> map = Map.of(sender.toString(), senderTransactionsSerialized, target.toString(), receiverTransactionsSerialized);

            //Save transactions into redis
            return currenciesManager.getRedisManager().getConnection(connection -> {
                connection.async().hset(TRANSACTIONS.toString(), map).thenAccept(response -> {
                    if (RedisEconomyPlugin.settings().DEBUG) {
                        Bukkit.getLogger().info("03b Transaction for " + sender + " saved in " + (System.currentTimeMillis() - init) + " ms with result " + response + " !");
                    }
                });
                return null;
            });
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    @SuppressWarnings("unused")
    /**
     * Saves a transaction
     *
     * @param accountOwner The owner of the account
     * @param target       Who transferred the money to the account owner. If it is the server the uuid will be UUID.fromString("Server")
     * @param amount       The amount of money transferred
     * @param currencyName The name of the currency
     * @param reason       The reason of the transaction
     */
    public void saveTransaction(UUID accountOwner, UUID target, double amount, String currencyName, String reason) {
        long init = System.currentTimeMillis();
        currenciesManager.getRedisManager().getConnection(connection -> {                                               //Get connection
                    RedisAsyncCommands<String, String> commands = connection.async();
                    return commands.hget(TRANSACTIONS.toString(), accountOwner.toString())                              //Get past transactions from redis
                            .thenApply(serializedTransactions -> {
                                if (RedisEconomyPlugin.settings().DEBUG) {
                                    Bukkit.getLogger().info("03c Retrieve single player transactions from redis... next 03d");
                                }
                                UUID correctedTarget = target;
                                if (target == null) {
                                    correctedTarget = UUID.fromString("Server");                                  //If the target is null, it means that the money was given/taken by the server
                                }
                                //Add the new transaction
                                String senderTransactionsSerialized = updateTransactionFromSerialized(                  //Update transactions adding the new one
                                        serializedTransactions,
                                        accountOwner, correctedTarget, amount,
                                        currencyName,
                                        reason);

                                //Save transaction into redis
                                return commands.hset(TRANSACTIONS.toString(), accountOwner.toString(), senderTransactionsSerialized) //Save transactions into redis
                                        .thenAccept(response -> {
                                            if (RedisEconomyPlugin.settings().DEBUG) {
                                                Bukkit.getLogger().info("03d Transaction for " + accountOwner + " saved in " + (System.currentTimeMillis() - init) + "ms with result " + response + " !");
                                            }
                                        });
                            }).exceptionally(throwable -> {
                                throwable.printStackTrace();
                                return null;
                            });
                }
        );
    }

    public String updateTransactionFromSerialized(String serializedTransactions, UUID account, UUID receiver, double amount, String currencyName, String reason) {
        Transaction[] spaceFreedTransactions = updateArraySpace(
                getTransactionsFromSerialized(serializedTransactions)
        );
        //Add the new transaction
        spaceFreedTransactions[spaceFreedTransactions.length - 1] = new Transaction(account, System.currentTimeMillis(), receiver, amount, currencyName, reason);
        return serializeTransactions(spaceFreedTransactions);
    }

    /**
     * Adds a space at the end of the array. If the array is full, the first element is removed.
     *
     * @param transactions The transactions to serialize
     * @return The serialized transactions with an empty space at the end
     */
    private Transaction[] updateArraySpace(Transaction[] transactions) {
        final int transactionMaxSize = RedisEconomyPlugin.settings().TRANSACTIONS_RETAINED;
        Transaction[] newTransactions;
        if (transactions.length > transactionMaxSize - 1) {
            newTransactions = new Transaction[transactionMaxSize];
            System.arraycopy(transactions, 1, newTransactions, 0, transactionMaxSize - 1);
        } else {
            newTransactions = new Transaction[transactions.length + 1];
            System.arraycopy(transactions, 0, newTransactions, 0, transactions.length);
        }
        return newTransactions;
    }

    /**
     * Deserializes a string into an array of transactions
     *
     * @param serialized The serialized transactions
     * @return The deserialized transactions
     */
    private Transaction[] getTransactionsFromSerialized(String serialized) {
        if (serialized == null)
            return new Transaction[0];
        String[] split = serialized.split("/");
        Transaction[] transactions = new Transaction[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                transactions[i] = Transaction.fromString(split[i]);
            } catch (Exception e) {
                transactions[i] = null;
            }
        }
        return transactions;
    }

    /**
     * Serializes an array of transactions into a string
     *
     * @param transactions The transactions to be serialized
     * @return The serialized transactions
     */
    public String serializeTransactions(Transaction[] transactions) {
        StringBuilder builder = new StringBuilder();
        for (Transaction transaction : transactions) {
            builder.append(transaction.toString()).append("/");
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }


}
