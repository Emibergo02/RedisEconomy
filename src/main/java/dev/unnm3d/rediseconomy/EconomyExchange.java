package dev.unnm3d.rediseconomy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@AllArgsConstructor
public class EconomyExchange {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisEconomy economy;

    public CompletableFuture<Transaction[]> getTransactions(String player) {
        return economy.getEzRedisMessenger().jedisResourceFuture(jedis -> getTransactionsFromJson(jedis.hget("rediseco:transactions", player)));
    }

    public void saveTransaction(String sender, String target, String amount) {
        economy.getEzRedisMessenger().jedisResourceFuture(jedis -> {

            //Retrieve all serialized transactions from redis
            List<String> lista = jedis.hmget("rediseco:transactions", sender, target);
            //Deserialize
            Transaction[] senderTransactions = getTransactionsFromJson(lista.get(0));
            Transaction[] receiverTransactions = getTransactionsFromJson(lista.get(1));

            //Add a space into arrays and delete the oldest transaction
            senderTransactions = updateArraySpace(senderTransactions);
            receiverTransactions = updateArraySpace(receiverTransactions);

            //Add the new transaction
            senderTransactions[senderTransactions.length - 1] = new Transaction(sender, target, "<red>-" + amount + "</red>", System.currentTimeMillis());
            receiverTransactions[receiverTransactions.length - 1] = new Transaction(sender, target, "<gold>+" + amount + "</gold>", System.currentTimeMillis());

            try {
                //Serialize and save transactions
                Map<String, String> map = Map.of(sender, objectMapper.writeValueAsString(senderTransactions), target, objectMapper.writeValueAsString(receiverTransactions));
                jedis.hmset("rediseco:transactions", map);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return jedis;
        });
    }

    private Transaction[] updateArraySpace(Transaction[] transactions) {
        final int transactionMaxSize=RedisEconomyPlugin.settings().TRANSACTIONS_RETAINED;
        Transaction[] newTransactions;
        if (transactions.length > transactionMaxSize-1) {
            newTransactions = new Transaction[transactionMaxSize];
            System.arraycopy(transactions, 1, newTransactions, 0, transactionMaxSize-1);
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

    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        public String sender = "";
        public String target = "";
        public String amount = "";
        public long timestamp = 0;
    }

}
