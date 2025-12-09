package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.CurrencySettings;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.storage.EconomyStorage;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.*;


@AllArgsConstructor
public class Currency implements Economy {
    protected final CurrenciesManager currenciesManager;
    @Getter
    private final EconomyStorage economyStorage;

    @Getter
    protected final String currencyName;
    private final ConcurrentHashMap<UUID, Double> accounts;
    private final ConcurrentHashMap<UUID, Double> maxPlayerBalances;

    private boolean enabled;
    @Getter
    private String currencySingular;
    @Getter
    private String currencyPlural;
    @Getter
    private final DecimalFormat decimalFormat;
    @Getter
    private final double startingBalance;
    @Getter
    private final double maxBalance;
    @Getter
    private boolean saveTransactions;
    @Getter
    private int transactionsTTL;
    @Getter
    private double transactionTax;
    @Getter
    private final boolean taxOnlyPay;
    protected final List<ExecutorService> updateExecutors;


    /**
     * Creates a new currency.
     * Currency implements Economy from Vault, so it's the same as using any other Vault Economy plugin
     *
     * @param currenciesManager The CurrenciesManager instance
     * @param economyStorage    The economy storage instance
     * @param currencySettings  The currency settings
     */
    public Currency(CurrenciesManager currenciesManager, EconomyStorage economyStorage, CurrencySettings currencySettings) {
        this.currenciesManager = currenciesManager;
        this.economyStorage = economyStorage;
        this.enabled = true;
        this.updateExecutors = generateExecutors(currencySettings.getExecutorThreads());
        this.currencyName = currencySettings.getCurrencyName();
        this.currencySingular = currencySettings.getCurrencySingle();
        this.currencyPlural = currencySettings.getCurrencyPlural();
        this.startingBalance = currencySettings.getStartingBalance();
        this.maxBalance = currencySettings.getMaxBalance() == 0.0d ? Double.POSITIVE_INFINITY : currencySettings.getMaxBalance();
        this.saveTransactions = currencySettings.isSaveTransactions();
        this.transactionsTTL = currencySettings.getTransactionsTTL();
        this.transactionTax = currencySettings.getPayTax();
        this.taxOnlyPay = currencySettings.isTaxOnlyPay();
        this.accounts = new ConcurrentHashMap<>();
        this.maxPlayerBalances = new ConcurrentHashMap<>();
        this.decimalFormat = new DecimalFormat(
                currencySettings.getDecimalFormat() != null ? currencySettings.getDecimalFormat() : "#.##",
                new DecimalFormatSymbols(Locale.forLanguageTag(currencySettings.getLanguageTag() != null ? currencySettings.getLanguageTag() : "en-US"))
        );

        economyStorage.getOrderedAccounts(currencyName, -1).thenApply(result -> {
            result.forEach(t ->
                    accounts.put(UUID.fromString(t.getValue()), t.getScore()));
            if (!accounts.isEmpty()) {
                RedisEconomyPlugin.debug("start1 Loaded " + accounts.size() + " accounts for currency " + currencyName);
            }
            return result;
        }).toCompletableFuture().join(); //Wait to avoid API calls before accounts are loaded

        economyStorage.getPlayerMaxBalances(currencyName).thenApply(result -> {
            maxPlayerBalances.putAll(result);
            if (!maxPlayerBalances.isEmpty()) {
                RedisEconomyPlugin.debug("start1 Loaded " + maxPlayerBalances.size() + " max balances for currency " + currencyName);
            }
            return result;
        }); //Not as critical as accounts, so we don't wait


    }

    public void processUpdateMessage(String channel, String[] arguments) {
        final UUID uuid = UUID.fromString(arguments[0]);

        if (channel.equals(RedisKeys.UPDATE_PLAYER_CHANNEL_PREFIX.toString())) {
            String playerName = arguments[1];
            double balance = Double.parseDouble(arguments[2]);
            if (playerName == null) {
                Bukkit.getLogger().severe("Player name not found for UUID " + uuid);
                return;
            }
            updateAccountLocal(uuid, playerName, balance);
            RedisEconomyPlugin.debug("01b Received balance update " + playerName + " to " + balance);

        } else if (channel.equals(RedisKeys.UPDATE_MAX_BAL_PREFIX.toString())) {
            double maxBal = Double.parseDouble(arguments[1]);
            setPlayerMaxBalanceLocal(uuid, maxBal);
            RedisEconomyPlugin.debug("01b Received max balance update " + uuid + " to " + maxBal);

        }
    }

    private List<ExecutorService> generateExecutors(int size) {
        if (size <= 0) return List.of(Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
        List<ExecutorService> executors = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            executors.add(Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
        }
        return executors;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldSaveTransactions() {
        return saveTransactions;
    }

    public void setShouldSaveTransactions(boolean saveTransactions) {
        this.saveTransactions = saveTransactions;
    }

    @Override
    public String getName() {
        return "RedisEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return decimalFormat.getMaximumFractionDigits();
    }

    @Override
    public String format(double amount) {
        return decimalFormat.format(amount) + (amount == 1 ? currencySingular : currencyPlural);
    }

    @Override
    public String currencyNamePlural() {
        return currencyPlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencySingular;
    }

    @Override
    public boolean hasAccount(@NotNull String playerName) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null) return false;
        return hasAccount(playerUniqueId);
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(@NotNull String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    public boolean hasAccount(@NotNull UUID playerUUID) {
        return accounts.containsKey(playerUUID);
    }

    public double getBalance(@NotNull UUID playerUUID) {
        return accounts.getOrDefault(playerUUID, 0.0D);
    }

    @Override
    public double getBalance(@NotNull String playerName) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null) return 0.0D;
        return getBalance(playerUniqueId);
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(@NotNull String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(@NotNull String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return has(player.getUniqueId(), amount);
    }

    public boolean has(@NotNull UUID playerUUID, double amount) {
        return getBalance(playerUUID) >= amount;
    }

    @Override
    public boolean has(@NotNull String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount) {
        return withdrawPlayer(playerName, amount, "Withdraw");
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, double amount) {
        return withdrawPlayer(player.getUniqueId(), player.getName(), amount, "Withdraw");
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, double amount) {
        return depositPlayer(playerName, amount, "Deposit");
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, double amount) {
        return depositPlayer(player.getUniqueId(), player.getName(), amount, "Deposit");
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return false;
        return createPlayerAccount(playerUUID, playerName);
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player) {
        return createPlayerAccount(player.getUniqueId(), player.getName());
    }

    public boolean createPlayerAccount(@NotNull UUID playerUUID, @Nullable String playerName) {
        if (hasAccount(playerUUID))
            return false;
        updateAccount(playerUUID, playerName, startingBalance);
        currenciesManager.getExchange().saveTransaction(new AccountID(playerUUID), new AccountID(), startingBalance, this, "Account creation");
        return true;
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName, @Nullable String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player, @Nullable String worldName) {
        return createPlayerAccount(player);
    }

    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount, @Nullable String reason) {
        UUID playerUniqueId = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUniqueId == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return withdrawPlayer(playerUniqueId, playerName, amount, reason);
    }


    public EconomyResponse withdrawPlayer(@NotNull UUID playerUUID, @Nullable String playerName, double amount, @Nullable String reason) {
        if (!hasAccount(playerUUID))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (taxOnlyPay ? 0d : amount * transactionTax);
        if (amountToWithdraw == Double.POSITIVE_INFINITY || amountToWithdraw == Double.NEGATIVE_INFINITY || Double.isNaN(amountToWithdraw))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");

        if (!has(playerUUID, amountToWithdraw))
            return new EconomyResponse(amountToWithdraw, getBalance(playerUUID), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        updateAccount(playerUUID, playerName, getBalance(playerUUID) - amountToWithdraw);
        currenciesManager.getExchange().saveTransaction(new AccountID(playerUUID), new AccountID(), -amountToWithdraw, this, reason == null ? "Withdraw" : reason);
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @SuppressWarnings("unused")
    public EconomyResponse payPlayer(@NotNull UUID sender, @NotNull UUID receiver, double amount, @Nullable String reason) {
        String senderName = currenciesManager.getUsernameFromUUIDCache(sender);
        if (senderName == null || !hasAccount(sender))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        //same for receiver
        String receiverName = currenciesManager.getUsernameFromUUIDCache(receiver);
        if (receiverName == null || !hasAccount(receiver))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (amount * transactionTax);

        if (amountToWithdraw == Double.POSITIVE_INFINITY || amountToWithdraw == Double.NEGATIVE_INFINITY || Double.isNaN(amountToWithdraw))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");

        if (!has(sender, amountToWithdraw))
            return new EconomyResponse(0, getBalance(sender), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        if (getBalance(receiver) + amount > getPlayerMaxBalance(receiver))
            return new EconomyResponse(0, getBalance(receiver), EconomyResponse.ResponseType.FAILURE, "The receiver has reached the maximum balance");

        updateAccount(sender, senderName, getBalance(sender) - amountToWithdraw);
        currenciesManager.getExchange().saveTransaction(new AccountID(sender), new AccountID(receiver), -amountToWithdraw, this, reason == null ? "Payment" : reason);
        updateAccount(sender, receiverName, getBalance(receiver) + amount);
        currenciesManager.getExchange().saveTransaction(new AccountID(receiver), new AccountID(sender), amount, this, reason == null ? "Payment" : reason);

        return new EconomyResponse(amount, getBalance(sender), EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse payPlayer(@NotNull String senderName, @NotNull String receiverName, double amount) {
        if (!hasAccount(senderName))
            return new EconomyResponse(amount, getBalance(senderName), EconomyResponse.ResponseType.FAILURE, "Account not found");
        if (!hasAccount(receiverName))
            return new EconomyResponse(amount, getBalance(receiverName), EconomyResponse.ResponseType.FAILURE, "Account not found");

        final UUID sender = currenciesManager.getUUIDFromUsernameCache(senderName);
        final UUID receiver = currenciesManager.getUUIDFromUsernameCache(receiverName);

        //Calculate the amount to withdraw with the transaction tax
        double amountToWithdraw = amount + (amount * transactionTax);
        if (sender == null || receiver == null)
            return new EconomyResponse(amount, getBalance(senderName), EconomyResponse.ResponseType.FAILURE, "Account not found");

        if (amountToWithdraw == Double.POSITIVE_INFINITY || amountToWithdraw == Double.NEGATIVE_INFINITY || Double.isNaN(amountToWithdraw))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");

        if (!has(senderName, amountToWithdraw))
            return new EconomyResponse(0, getBalance(sender), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        if (getBalance(receiver) + amount > getPlayerMaxBalance(receiver))
            return new EconomyResponse(0, getBalance(receiver), EconomyResponse.ResponseType.FAILURE, "The receiver has reached the maximum balance");

        updateAccount(sender, senderName, getBalance(sender) - amountToWithdraw);
        updateAccount(receiver, receiverName, getBalance(receiver) + amount);

        return new EconomyResponse(amount, getBalance(sender), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Set the balance of a player
     *
     * @param player The player to set the balance of
     * @param amount The amount to set the balance to
     * @return The result of the operation
     */
    @SuppressWarnings("unused")
    public EconomyResponse setPlayerBalance(@NotNull OfflinePlayer player, double amount) {
        return setPlayerBalance(player.getUniqueId(), player.getName(), amount);
    }

    /**
     * Set the balance of a player
     *
     * @param playerUUID The player uuid to set the balance of
     * @param amount     The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(@NotNull UUID playerUUID, @Nullable String playerName, double amount) {
        if (amount == Double.POSITIVE_INFINITY || amount == Double.NEGATIVE_INFINITY || Double.isNaN(amount))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");
        updateAccount(playerUUID, playerName, amount);
        currenciesManager.getExchange().saveTransaction(new AccountID(playerUUID), new AccountID(), -getBalance(playerUUID), this, "Reset balance");
        currenciesManager.getExchange().saveTransaction(new AccountID(playerUUID), new AccountID(), amount, this, "Set balance");
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Revert a transaction
     *
     * @param transactionId The transaction id
     * @param transaction   The transaction to revert
     * @return The transaction id that reverted the initial transaction
     */
    public CompletionStage<Long> revertTransaction(long transactionId, @NotNull Transaction transaction) {
        String ownerName = transaction.getAccountIdentifier().isPlayer() ?//If the sender is a player
                currenciesManager.getUsernameFromUUIDCache(transaction.getAccountIdentifier().getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.getAccountIdentifier().toString(); //Else, it's a bank, so we get the bank id
        if (transaction.getAccountIdentifier().isPlayer()) {
            updateAccount(transaction.getAccountIdentifier().getUUID(), ownerName, getBalance(transaction.getAccountIdentifier().getUUID()) - transaction.getAmount());
        }
        RedisEconomyPlugin.debug("revert01a reverted on account " + transaction.getAccountIdentifier() + " amount " + transaction.getAmount());

        return currenciesManager.getExchange().saveTransaction(transaction.getAccountIdentifier(), transaction.getActor(), -transaction.getAmount(), this, "Revert #" + transactionId + ": " + transaction.getReason());
    }

    /**
     * Set the balance of a player
     *
     * @param playerName The player to set the balance of
     * @param amount     The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(@NotNull String playerName, double amount) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return setPlayerBalance(playerUUID, playerName, amount);
    }

    public EconomyResponse depositPlayer(@NotNull String playerName, double amount, @Nullable String reason) {
        UUID playerUUID = currenciesManager.getUUIDFromUsernameCache(playerName);
        if (playerUUID == null)
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        return depositPlayer(playerUUID, playerName, amount, reason);
    }

    public EconomyResponse depositPlayer(@NotNull UUID playerUUID, @Nullable String playerName, double amount, String reason) {
        if (!hasAccount(playerUUID))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");

        if (amount == Double.POSITIVE_INFINITY || amount == Double.NEGATIVE_INFINITY || Double.isNaN(amount))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid decimal amount format");

        if (getBalance(playerUUID) + amount > getPlayerMaxBalance(playerUUID))
            return new EconomyResponse(0, getBalance(playerUUID), EconomyResponse.ResponseType.FAILURE, "The player has reached the maximum balance");

        updateAccount(playerUUID, playerName, getBalance(playerUUID) + amount);
        currenciesManager.getExchange().saveTransaction(new AccountID(playerUUID), new AccountID(), amount, this, reason == null ? "Deposit" : reason);
        return new EconomyResponse(amount, getBalance(playerUUID), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Only update the balance on local memory
     *
     * @param uuid       The UUID of the player
     * @param playerName The name of the player, can be null if not known
     * @param balance    The new balance to set for the player
     */
    public void updateAccountLocal(@NotNull UUID uuid, @Nullable String playerName, double balance) {
        if (playerName != null)
            currenciesManager.updateNameUniqueId(playerName, uuid);
        accounts.put(uuid, balance);
    }

    protected void updateAccount(@NotNull UUID uuid, @Nullable String playerName, double balance) {
        updateAccountCloudCache(uuid, playerName, balance, 0);
        updateAccountLocal(uuid, playerName, balance);
    }

    private synchronized void updateAccountCloudCache(@NotNull UUID uuid, @Nullable String playerName, double balance, int tries) {
        CompletableFuture.supplyAsync(() -> {
            RedisEconomyPlugin.debugCache("01a Starting update account " + playerName + " to " + balance + " currency " + currencyName);

            economyStorage.updateAccount(currencyName, uuid, playerName, balance)
                    .ifPresentOrElse(result -> {
                        RedisEconomyPlugin.debugCache("01c Sent update account successfully " + playerName + " to " + balance + " currency " + currencyName);
                    }, () -> handleException(uuid, playerName, balance, tries, null));

            return null;
        }, getExecutor((int) uuid.getMostSignificantBits())).orTimeout(10, TimeUnit.SECONDS).exceptionally(throwable -> {
            handleException(uuid, playerName, balance, tries, new Exception(throwable));
            return null;
        });
    }

    private void handleException(@NotNull UUID uuid, @Nullable String playerName, double balance, int tries, @Nullable Exception e) {
        final RedisEconomyPlugin plugin = RedisEconomyPlugin.getInstance();
        if (tries < plugin.settings().redis.getTryAgainCount()) {
            RedisEconomyPlugin.debugCache("WARN! Player accounts are desynchronized. try: " + tries);
            if (plugin.settings().debugUpdateCache) {
                if (e instanceof RedisCommandTimeoutException) {
                    plugin.getLogger().warning("This is probably a network issue. " +
                            "Try to increase the timeout parameter in the config.yml and ask the creator of the plugin what to do");
                }
                if (e != null)
                    e.printStackTrace();
            }
            updateAccountCloudCache(uuid, playerName, balance, tries + 1);
            return;
        }
        if (plugin.settings().debugUpdateCache) {
            plugin.getLogger().severe("Failed to update account " + playerName + " after " + tries + " tries");
            RedisEconomyPlugin.debugCache("ERROR! Failed to update account " + playerName + " after " + tries + " tries");
            currenciesManager.getRedisManager().printPool();
            if (e != null)
                e.printStackTrace();
        }
    }

    /**
     * Be sure that every account is on the same executor to avoid de-synchronization
     *
     * @param identifier The identifier of the account
     * @return The executor to use
     */
    protected ExecutorService getExecutor(int identifier) {
        return updateExecutors.get((Math.abs(identifier) % updateExecutors.size()));
    }


    public double getPlayerMaxBalance(UUID uuid) {
        return maxPlayerBalances.getOrDefault(uuid, maxBalance);
    }

    public void setPlayerMaxBalance(UUID uuid, double maxAmount) {
        if (maxAmount < getBalance(uuid)) {
            setPlayerBalance(uuid, null, maxAmount);
        }
        economyStorage.updatePlayerMaxBalance(currencyName, uuid, maxAmount, maxBalance);
        setPlayerMaxBalanceLocal(uuid, maxAmount);
    }

    private void setPlayerMaxBalanceLocal(UUID uuid, double amount) {
        maxPlayerBalances.put(uuid, amount);
    }

    /**
     * Get all accounts in cache
     *
     * @return Unmodifiable map of accounts
     */
    @SuppressWarnings("unused")
    public final Map<UUID, Double> getAccounts() {
        return Collections.unmodifiableMap(accounts);
    }

    /**
     * Terminate all executors
     */
    public void terminateExecutors() {
        updateExecutors.forEach(executor -> {
            try {
                if (!executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e1) {
                executor.shutdownNow();
            }
        });
    }

}
