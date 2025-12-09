package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.CurrencySettings;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.ScoredValue;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;

public class CurrencyWithBanks extends Currency {
    private final ConcurrentHashMap<String, Double> bankAccounts;
    /**
     * Account ID -> Owner UUID
     */
    private final ConcurrentHashMap<String, UUID> bankOwners;

    public CurrencyWithBanks(CurrenciesManager currenciesManager, EconomyStorage economyStorage, CurrencySettings currencySettings) {
        super(currenciesManager, economyStorage, currencySettings);
        bankAccounts = new ConcurrentHashMap<>();
        bankOwners = new ConcurrentHashMap<>();
        economyStorage.getOrderedBankAccounts(currencyName).thenApply(list -> {
            for (ScoredValue<String> scoredValue : list) {
                bankAccounts.put(scoredValue.getValue(), scoredValue.getScore());
            }
            if (!bankAccounts.isEmpty()) {
                RedisEconomyPlugin.debug("start1bank Loaded " + bankAccounts.size() + " accounts for currency " + currencyName);
            }
            return list;
        }).toCompletableFuture().join();
        economyStorage.getBankOwners().thenApply(map -> {
            map.forEach((k, v) -> bankOwners.put(k, UUID.fromString(v)));
            if (!bankOwners.isEmpty()) {
                RedisEconomyPlugin.debug("start1bankb Loaded " + bankOwners.size() + " accounts owners for currency " + currencyName);
            }
            return map;
        }).toCompletableFuture().join();

    }

    @Override
    public void processUpdateMessage(String channel, String[] arguments) {
        if (channel.equals(RedisKeys.UPDATE_BANK_OWNER_CHANNEL_PREFIX.toString())) {
            String accountId = arguments[0];
            UUID owner = UUID.fromString(arguments[1]);
            bankOwners.put(accountId, owner);
            RedisEconomyPlugin.debug("01d Received bank owner update " + accountId + " to " + owner);
        } else if (channel.equals(RedisKeys.UPDATE_BANK_CHANNEL_PREFIX.toString())) {
            String accountId = arguments[0];
            double balance = Double.parseDouble(arguments[1]);
            updateBankAccountLocal(accountId, balance);
            RedisEconomyPlugin.debug("01c Received bank balance update " + accountId + " to " + balance);
        } else super.processUpdateMessage(channel, arguments);
    }

    @Override
    public boolean hasBankSupport() {
        return true;
    }

    public EconomyResponse createBank(@NotNull String accountId) {
        return createBank(accountId, RedisKeys.getServerUUID(), "Bank account creation");
    }

    @Override
    public EconomyResponse createBank(@NotNull String accountId, String player) {
        return createBank(accountId, currenciesManager.getUUIDFromUsernameCache(player), "Bank account creation");
    }

    /**
     * Creates a bank account
     *
     * @param accountId   Account ID
     * @param playerOwner Owner UUID
     * @param reason      Reason for the creation
     * @return EconomyResponse with the result of the operation (SUCCESS)
     */
    public EconomyResponse createBank(@NotNull String accountId, UUID playerOwner, String reason) {
        if (bankAccounts.containsKey(accountId))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank account already exists");
        setOwner(accountId, playerOwner);
        updateBankAccount(accountId, 0);
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(playerOwner), 0, this, reason);
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse createBank(@NotNull String accountId, OfflinePlayer player) {
        return createBank(accountId, player.getUniqueId(), "Bank account creation");
    }

    public boolean existsBank(@NotNull String accountId) {
        return bankAccounts.containsKey(accountId);
    }

    @Override
    public EconomyResponse deleteBank(@NotNull String accountId) {
        currenciesManager.getRedisManager().getConnectionPipeline(redisAsyncCommands -> {
            redisAsyncCommands.zrem(BALANCE_BANK_PREFIX + currencyName, accountId);
            return redisAsyncCommands.hdel(BANK_OWNERS.toString(), accountId);
        }).thenAccept(result -> {
            bankAccounts.remove(accountId);
            RedisEconomyPlugin.debug("Deleted bank account " + accountId + " with result " + result);

            currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), 0, this, "Bank account deletion");
        });
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankBalance(@NotNull String accountId) {
        return new EconomyResponse(0, bankAccounts.getOrDefault(accountId, 0.0D), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankHas(@NotNull String accountId, double amount) {
        if (!existsBank(accountId)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank account does not exist");
        }
        double bankBalance = bankAccounts.getOrDefault(accountId, 0.0D);
        if (bankBalance - amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not enough money");
        }
        return new EconomyResponse(amount, bankBalance - amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankWithdraw(@NotNull String accountId, double amount) {
        return bankWithdraw(accountId, amount, "Bank withdraw");
    }

    /**
     * Withdraws money from a bank account with a reason
     *
     * @param accountId Account ID
     * @param amount    Amount to withdraw
     * @param reason    Reason for the withdraw
     * @return EconomyResponse with the result of the operation SUCCESS or FAILURE if there is not enough money
     */
    public EconomyResponse bankWithdraw(@NotNull String accountId, double amount, String reason) {
        EconomyResponse hasAmountResponse = bankHas(accountId, amount);
        if (hasAmountResponse.type.equals(EconomyResponse.ResponseType.FAILURE)) {
            return hasAmountResponse;
        }
        updateBankAccount(accountId, hasAmountResponse.balance);//Balance is the new balance with subtracted amount
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), -amount, this, reason);
        return new EconomyResponse(amount, hasAmountResponse.balance, EconomyResponse.ResponseType.SUCCESS, null);

    }

    @Override
    public EconomyResponse bankDeposit(@NotNull String accountId, double amount) {
        return bankDeposit(accountId, amount, "Bank deposit");
    }

    /**
     * Deposits money to a bank account with a reason
     *
     * @param accountId Account ID
     * @param amount    Amount to deposit
     * @param reason    Reason for the deposit
     * @return EconomyResponse with the result of the operation SUCCESS or FAILURE if the bank account does not exist
     */
    public EconomyResponse bankDeposit(@NotNull String accountId, double amount, String reason) {
        if (!existsBank(accountId)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank account does not exist");
        }
        double bankBalance = bankAccounts.getOrDefault(accountId, 0.0D);
        updateBankAccount(accountId, bankBalance + amount);//Balance is the new balance with subtracted amount
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), amount, this, reason);
        return new EconomyResponse(amount, bankBalance + amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Set the balance of a bank account.
     *
     * @param accountId The account ID of the bank
     * @param amount    The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setBankBalance(@NotNull String accountId, double amount) {
        if (amount == Double.POSITIVE_INFINITY || amount == Double.NEGATIVE_INFINITY || Double.isNaN(amount))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");
        updateBankAccount(accountId, amount);
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), -bankAccounts.getOrDefault(accountId, 0.0D), this, "Reset balance");
        currenciesManager.getExchange().saveTransaction(new AccountID(accountId), new AccountID(), amount, this, "Set balance");
        return new EconomyResponse(amount, bankAccounts.getOrDefault(accountId, 0.0D), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, String playerName) {
        if (bankOwners.getOrDefault(accountId, null).equals(currenciesManager.getUUIDFromUsernameCache(playerName))) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not owner");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, OfflinePlayer player) {
        if (bankOwners.getOrDefault(accountId, null).equals(player.getUniqueId())) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not owner");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String accountId, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String accountId, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public List<String> getBanks() {
        return bankAccounts.keySet().stream().toList();
    }

    /**
     * Revert a transaction
     *
     * @param transactionId The transaction id
     * @param transaction   The transaction to revert
     * @return The transaction id that reverted the initial transaction
     */
    @Override
    public CompletionStage<Long> revertTransaction(long transactionId, @NotNull Transaction transaction) {
        String ownerName = transaction.getAccountIdentifier().isPlayer() ?//If the sender is a player
                currenciesManager.getUsernameFromUUIDCache(transaction.getAccountIdentifier().getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.getAccountIdentifier().toString(); //Else, it's a bank, so we get the bank id
        if (transaction.getAccountIdentifier().isPlayer()) {//Update player account
            updateAccount(transaction.getAccountIdentifier().getUUID(), ownerName, getBalance(transaction.getAccountIdentifier().getUUID()) - transaction.getAmount());
        } else {//Update bank account
            double bankBalance = bankAccounts.getOrDefault(transaction.getAccountIdentifier().toString(), 0.0D);
            updateBankAccount(transaction.getAccountIdentifier().toString(), bankBalance - transaction.getAmount());
        }
        RedisEconomyPlugin.debug("revert01a reverted on account " + transaction.getAccountIdentifier() + " amount " + transaction.getAmount());

        return currenciesManager.getExchange().saveTransaction(transaction.getAccountIdentifier(), transaction.getActor(), -transaction.getAmount(), this, "Revert #" + transactionId + ": " + transaction.getReason());
    }

    private void setOwner(@NotNull String accountId, UUID ownerUUID) {
        currenciesManager.getRedisManager().getConnectionPipeline(connection -> {
            connection.hset(BANK_OWNERS.toString(), accountId, ownerUUID.toString());
            return connection.publish(UPDATE_BANK_OWNER_CHANNEL_PREFIX + currencyName, RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + accountId + ";;" + ownerUUID);
        }).thenAccept((result) -> {
            RedisEconomyPlugin.debug("Set owner of bank " + accountId + " to " + ownerUUID + " with result " + result);

            bankOwners.put(accountId, ownerUUID);
        });
    }

    void updateBankAccountLocal(String accountId, double balance) {
        bankAccounts.put(accountId, balance);
    }

    private void updateBankAccount(@NotNull String accountId, double balance) {
        updateBankAccountCloudCache(accountId, balance, 0);
        updateBankAccountLocal(accountId, balance);
    }

    private synchronized void updateBankAccountCloudCache(@NotNull String accountId, double balance, int tries) {
        CompletableFuture.supplyAsync(() -> {
            RedisEconomyPlugin.debugCache("01a Starting update bank account " + accountId + " to " + balance + " currency " + currencyName);

            currenciesManager.getRedisManager().executeTransaction(reactiveCommands -> {
                reactiveCommands.zadd(BALANCE_BANK_PREFIX + currencyName, balance, accountId);
                reactiveCommands.publish(UPDATE_BANK_CHANNEL_PREFIX + currencyName,
                        RedisEconomyPlugin.getInstanceUUID().toString() + ";;" + accountId + ";;" + balance);
                RedisEconomyPlugin.debugCache("01b Publishing update bank account " + accountId + " to " + balance + " currency " + currencyName);

            }).ifPresentOrElse(result -> {
                RedisEconomyPlugin.debugCache("01c Sent bank update account " + accountId + " to " + balance);
            }, () -> handleException(accountId, balance, tries, null));

            return null;
        }, getExecutor(accountId.getBytes()[accountId.length() - 1])).orTimeout(10, TimeUnit.SECONDS).exceptionally(throwable -> {
            handleException(accountId, balance, tries, new Exception(throwable));
            return null;
        });
    }

    private void handleException(@NotNull String accountId, double balance, int tries, @Nullable Exception e) {
        final RedisEconomyPlugin plugin = RedisEconomyPlugin.getInstance();
        if (tries < plugin.settings().redis.getTryAgainCount()) {
            plugin.getLogger().warning("Player accounts are desynchronized. try: " + tries);
            if (e instanceof RedisCommandTimeoutException) {
                plugin.getLogger().warning("This is probably a network issue. " +
                        "Try to increase the timeout parameter in the config.yml and ask the creator of the plugin what to do");
            }
            if (e != null)
                e.printStackTrace();
            updateBankAccountCloudCache(accountId, balance, tries + 1);
        } else {
            plugin.getLogger().severe("Failed to update bank account " + accountId + " after " + tries + " tries");
            currenciesManager.getRedisManager().printPool();
            if (e != null)
                e.printStackTrace();
        }
    }


}
