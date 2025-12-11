package dev.unnm3d.rediseconomy.storage;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.currency.CurrencyWithBanks;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class FileStorageService {
    private final RedisEconomyPlugin plugin;
    private final File dataFile;
    private FileStorageState state;
    private volatile boolean transactionsDirty;

    public FileStorageService(RedisEconomyPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "storage.dat");
        this.state = loadState();
    }

    private synchronized FileStorageState loadState() {
        if (!dataFile.exists()) {
            return new FileStorageState();
        }
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(dataFile))) {
            Object read = objectInputStream.readObject();
            if (read instanceof FileStorageState storageState) {
                return storageState;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load file storage: " + e.getMessage());
        }
        return new FileStorageState();
    }

    private synchronized void saveState() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(dataFile))) {
                objectOutputStream.writeObject(state);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save file storage: " + e.getMessage());
        }
    }

    public synchronized Map<String, UUID> loadNameUniqueIds() {
        Map<String, UUID> map = new HashMap<>();
        state.nameUniqueIds.forEach((name, uuid) -> map.put(name, UUID.fromString(uuid)));
        return map;
    }

    public synchronized Map<UUID, List<UUID>> loadLockedAccounts() {
        Map<UUID, List<UUID>> map = new HashMap<>();
        state.lockedAccounts.forEach((uuid, list) -> map.put(UUID.fromString(uuid),
                list.stream().map(UUID::fromString).toList()));
        return map;
    }

    public synchronized Map<UUID, Double> loadCurrencyAccounts(String currencyName) {
        return state.currencyAccounts.getOrDefault(currencyName, Map.of()).entrySet().stream()
                .collect(Collectors.toMap(entry -> UUID.fromString(entry.getKey()), Map.Entry::getValue));
    }

    public synchronized Map<UUID, Double> loadCurrencyMaxBalances(String currencyName) {
        return state.currencyMaxBalances.getOrDefault(currencyName, Map.of()).entrySet().stream()
                .collect(Collectors.toMap(entry -> UUID.fromString(entry.getKey()), Map.Entry::getValue));
    }

    public synchronized Map<String, Double> loadBankAccounts(String currencyName) {
        return new HashMap<>(state.bankAccounts.getOrDefault(currencyName, Map.of()));
    }

    public synchronized Map<String, UUID> loadBankOwners(String currencyName) {
        return state.bankOwners.getOrDefault(currencyName, Map.of()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> UUID.fromString(entry.getValue())));
    }

    public synchronized void saveSnapshot(CurrenciesManager currenciesManager) {
        if (!currenciesManager.isFileStorage()) return;
        boolean shouldSave = currenciesManager.consumeDirtyFlag() || transactionsDirty;
        if (!shouldSave) return;

        FileStorageState newState = new FileStorageState();
        newState.transactionCounter = state.transactionCounter;
        newState.transactions = new HashMap<>(state.transactions);

        currenciesManager.getNameUniqueIds().forEach((name, uuid) ->
                newState.nameUniqueIds.put(name, uuid.toString()));

        currenciesManager.getLockedAccountsMap().forEach((uuid, list) ->
                newState.lockedAccounts.put(uuid.toString(), list.stream().map(UUID::toString).toList()));

        for (Currency currency : currenciesManager.getCurrencies()) {
            Map<String, Double> accounts = new HashMap<>();
            currency.getAccounts().forEach((uuid, balance) -> accounts.put(uuid.toString(), balance));
            newState.currencyAccounts.put(currency.getCurrencyName(), accounts);

            Map<String, Double> maxBalances = new HashMap<>();
            currency.getMaxPlayerBalances().forEach((uuid, balance) -> maxBalances.put(uuid.toString(), balance));
            newState.currencyMaxBalances.put(currency.getCurrencyName(), maxBalances);

            if (currency instanceof CurrencyWithBanks currencyWithBanks) {
                Map<String, Double> bankAccounts = new HashMap<>(currencyWithBanks.getBankAccounts());
                newState.bankAccounts.put(currency.getCurrencyName(), bankAccounts);

                Map<String, String> bankOwners = new HashMap<>();
                currencyWithBanks.getBankOwners().forEach((id, uuid) -> bankOwners.put(id, uuid.toString()));
                newState.bankOwners.put(currency.getCurrencyName(), bankOwners);
            }
        }
        transactionsDirty = false;
        state = newState;
        saveState();
    }

    public synchronized long nextTransactionId() {
        state.transactionCounter++;
        transactionsDirty = true;
        return state.transactionCounter;
    }

    public synchronized long getTransactionCounter() {
        return state.transactionCounter;
    }

    public synchronized Transaction getTransaction(AccountID accountId, long id) {
        String stored = state.transactions.getOrDefault(accountId.toString(), Map.of()).get(id);
        return stored == null ? null : Transaction.fromString(stored);
    }

    public synchronized TreeMap<Long, Transaction> getTransactions(AccountID accountId, int limit) {
        TreeMap<Long, Transaction> map = new TreeMap<>();
        state.transactions.getOrDefault(accountId.toString(), Map.of()).entrySet().stream()
                .sorted(Map.Entry.<Long, String>comparingByKey().reversed())
                .limit(limit)
                .forEach(entry -> map.put(entry.getKey(), Transaction.fromString(entry.getValue())));
        return map;
    }

    public synchronized void saveTransaction(AccountID accountId, long id, Transaction transaction) {
        state.transactions.computeIfAbsent(accountId.toString(), s -> new HashMap<>()).put(id, transaction.toString());
        transactionsDirty = true;
    }

    public synchronized long clearTransactions() {
        long size = state.transactions.values().stream().mapToLong(Map::size).sum();
        state.transactions.clear();
        state.transactionCounter = 0;
        transactionsDirty = true;
        return size;
    }

    private static class FileStorageState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        Map<String, Map<String, Double>> currencyAccounts = new HashMap<>();
        Map<String, Map<String, Double>> currencyMaxBalances = new HashMap<>();
        Map<String, Map<String, Double>> bankAccounts = new HashMap<>();
        Map<String, Map<String, String>> bankOwners = new HashMap<>();
        Map<String, String> nameUniqueIds = new HashMap<>();
        Map<String, List<String>> lockedAccounts = new HashMap<>();
        Map<String, Map<Long, String>> transactions = new HashMap<>();
        long transactionCounter = 0L;
    }
}
