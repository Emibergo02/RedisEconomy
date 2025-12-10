package dev.unnm3d.rediseconomy.storage;

import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import io.lettuce.core.ScoredValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for economy data storage operations.
 * Provides methods to retrieve and update economy-related data from various storage backends.
 * This abstraction allows for future implementations such as file-based storage.
 */
public interface EconomyStorage {

    /**
     * Get ordered accounts from storage
     * Accounts are ordered by balance in descending order
     *
     * @param currencyName The currency name
     * @param limit        The number of accounts to return from the top (use -1 for all)
     * @return A list of accounts ordered by balance in ScoredValue format (value is UUID string, score is balance)
     */
    CompletionStage<List<ScoredValue<String>>> getOrderedAccounts(String currencyName, int limit);

    /**
     * Get single account balance from storage
     *
     * @param currencyName The currency name
     * @param uuid         The UUID of the account
     * @return The balance associated with the UUID
     */
    CompletionStage<Double> getAccountBalance(String currencyName, UUID uuid);

    /**
     * Get all player max balances for a currency
     *
     * @param currencyName The currency name
     * @return A map of UUID to max balance
     */
    CompletionStage<Map<UUID, Double>> getPlayerMaxBalances(String currencyName);

    /**
     * Get ordered bank accounts from storage
     * Bank accounts are ordered by balance in descending order
     *
     * @param currencyName The currency name
     * @return A list of bank accounts ordered by balance
     */
    CompletionStage<List<ScoredValue<String>>> getOrderedBankAccounts(String currencyName);

    /**
     * Get bank owners from storage
     *
     * @return A map of bank account ID to owner UUID (as strings)
     */
    CompletionStage<Map<String, String>> getBankOwners();

    /**
     * Load name-UUID mappings from storage
     *
     * @return A concurrent hash map of player names to UUIDs
     */
    CompletionStage<ConcurrentHashMap<String, UUID>> loadNameUniqueIds();

    /**
     * Load locked accounts from storage
     *
     * @return A concurrent hash map of UUID to list of locked UUIDs
     */
    CompletionStage<ConcurrentHashMap<UUID, List<UUID>>> loadLockedAccounts();

    /**
     * Get transactions for an account
     *
     * @param accountId The account ID
     * @return A map of transaction IDs to transaction data (as strings)
     */
    CompletionStage<Map<String, String>> getTransactions(AccountID accountId);

    /**
     * Get the current transaction counter value
     *
     * @return The current transaction counter, or null if not set
     */
    CompletionStage<String> getCurrentTransactionCounter();

    // Write operations

    /**
     * Update account balance and optionally name-UUID mapping
     *
     * @param currencyName The currency name
     * @param uuid         The player UUID
     * @param playerName   The player name (can be null)
     * @param balance      The new balance
     * @return Optional with transaction result or empty if failed
     */
    Optional<List<Object>> updateAccount(String currencyName, UUID uuid, String playerName, double balance);

    /**
     * Bulk update accounts and name-UUID mappings
     *
     * @param currencyName The currency name
     * @param balances     The balances to update
     * @param nameUUIDs    The name-UUID mappings to update
     * @return Optional with transaction result or empty if failed
     */
    Optional<List<Object>> updateBulkAccounts(String currencyName, List<ScoredValue<String>> balances, Map<String, String> nameUUIDs);

    /**
     * Update or delete player max balance
     *
     * @param currencyName The currency name
     * @param uuid         The player UUID
     * @param amount       The max balance amount
     * @param defaultMax   The default max balance (if amount equals this, delete the entry)
     */
    void updatePlayerMaxBalance(String currencyName, UUID uuid, double amount, double defaultMax);

    /**
     * Remove name-UUID mappings
     *
     * @param toRemove The names to remove
     */
    void removeNameUniqueIds(Map<String, UUID> toRemove);

    /**
     * Switch currency accounts between two currencies
     *
     * @param oldCurrencyName The old currency name
     * @param newCurrencyName The new currency name
     */
    void switchCurrency(String oldCurrencyName, String newCurrencyName);

    /**
     * Update locked accounts
     *
     * @param uuid        The account UUID
     * @param redisString The locked accounts as comma-separated string
     * @return CompletionStage that completes when the update is published
     */
    CompletionStage<Long> updateLockedAccounts(UUID uuid, String redisString);

    // Bank operations

    /**
     * Delete a bank account
     *
     * @param currencyName The currency name
     * @param accountId    The bank account ID
     * @return CompletionStage with the number of entries deleted
     */
    CompletionStage<Long> deleteBank(String currencyName, String accountId);

    /**
     * Set the owner of a bank account
     *
     * @param accountId The bank account ID
     * @param ownerUUID The owner UUID
     * @return CompletionStage that completes when the update is published
     */
    CompletionStage<Long> setBankOwner(String currencyName, String accountId, UUID ownerUUID);

    /**
     * Update bank account balance
     *
     * @param currencyName The currency name
     * @param accountId    The bank account ID
     * @param balance      The new balance
     * @return Optional with transaction result or empty if failed
     */
    Optional<List<Object>> updateBankAccount(String currencyName, String accountId, double balance);
}

