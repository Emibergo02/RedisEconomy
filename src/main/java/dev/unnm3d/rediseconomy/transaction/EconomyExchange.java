package dev.unnm3d.rediseconomy.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@AllArgsConstructor
public class EconomyExchange {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final CurrenciesManager currenciesManager;

    public CompletableFuture<Transaction[]> getTransactions(UUID player) {
        try (StatefulRedisConnection<String, String> connection = currenciesManager.getRedisClient().connect()) {
            return connection.async().hget("rediseco:transactions", player.toString()).thenApply(this::getTransactionsFromJson).toCompletableFuture();
        }
    }

    public void saveTransaction(UUID sender, UUID target, double amount) {
        try (StatefulRedisConnection<String, String> connection = currenciesManager.getRedisClient().connect()) {
            RedisAsyncCommands<String,String> commands=connection.async();

            commands.hmget("rediseco:transactions", sender.toString(), target.toString()).thenAccept(lista->{
            //Deserialize
            Transaction[] senderTransactions = getTransactionsFromJson(lista.get(0).getValue());
            Transaction[] receiverTransactions = getTransactionsFromJson(lista.get(1).getValue());

            //Add a space into arrays and delete the oldest transaction
            senderTransactions = updateArraySpace(senderTransactions);
            receiverTransactions = updateArraySpace(receiverTransactions);

            //Add the new transaction
            senderTransactions[senderTransactions.length - 1] = new Transaction(sender, System.currentTimeMillis(), target, -amount,"vault","Payment");
            receiverTransactions[receiverTransactions.length - 1] = new Transaction(sender, System.currentTimeMillis(), target, amount,"vault","Payment");

            try {
                //Serialize and save transactions
                Map<String, String> map = Map.of(sender.toString(), objectMapper.writeValueAsString(senderTransactions), target.toString(), objectMapper.writeValueAsString(receiverTransactions));
                commands.hmset("rediseco:transactions", map);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        }
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
