package dev.unnm3d.rediseconomy.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final CurrenciesManager currenciesManager;

    public CompletableFuture<Transaction[]> getTransactions(UUID player) {
        return currenciesManager.getRedisManager().getConnection(connection -> {
            connection.setTimeout(Duration.ofMillis(1000));
            return connection.async().hget(TRANSACTIONS.toString(), player.toString()).thenApply(this::getTransactionsFromJson).toCompletableFuture();
        });


    }

    public void saveTransaction(RedisAsyncCommands<String, String> client, UUID sender, UUID target, double amount) {
        client.hmget(TRANSACTIONS.toString(), sender.toString(), target.toString()).thenApply(lista -> {
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("03 Retrieve transactions from redis... next 02");
            }
            //Deserialize
            Transaction[] senderTransactions = getTransactionsFromJson(lista.get(0).isEmpty() ? null : lista.get(0).getValue());
            Transaction[] receiverTransactions = getTransactionsFromJson(lista.get(1).isEmpty() ? null : lista.get(1).getValue());

            //Add a space into arrays and delete the oldest transaction
            senderTransactions = updateArraySpace(senderTransactions);
            receiverTransactions = updateArraySpace(receiverTransactions);

            //Add the new transaction
            senderTransactions[senderTransactions.length - 1] = new Transaction(sender, System.currentTimeMillis(), target, -amount, "vault", "Payment");
            receiverTransactions[receiverTransactions.length - 1] = new Transaction(sender, System.currentTimeMillis(), target, amount, "vault", "Payment");

            //Serialize transactions
            try {
                return Map.of(sender.toString(), objectMapper.writeValueAsString(senderTransactions), target.toString(), objectMapper.writeValueAsString(receiverTransactions));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        }).thenAccept(map -> { //Save transactions
            currenciesManager.getRedisManager().getConnection(connection -> {
                connection.sync().hmset(TRANSACTIONS.toString(), map);
                if (RedisEconomyPlugin.settings().DEBUG) {
                    Bukkit.getLogger().info("03b Transaction for " + sender + " saved!");
                }
                return null;
            });
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });

    }

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

    private Transaction[] getTransactionsFromJson(String serialized) {
        if (serialized == null)
            return new Transaction[0];
        try {
            return objectMapper.readValue(serialized, Transaction[].class);
        } catch (JsonProcessingException e) {
            return new Transaction[0];
        }
    }


}
