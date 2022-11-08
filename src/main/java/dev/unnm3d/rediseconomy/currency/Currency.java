package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.jedis.Pipeline;
import dev.unnm3d.jedis.resps.Tuple;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public class Currency implements Economy {

    private final CurrenciesManager currenciesManager;
    private boolean enabled;
    @Getter
    private final String currencyName;
    @Getter
    private String currencySingular;
    @Getter
    private String currencyPlural;
    @Getter
    private double startingBalance;
    @Getter
    private double transactionTax;
    private final ConcurrentHashMap<UUID, Double> accounts;


    /**
     * Creates a new currency.
     * Currency implements Economy from Vault, so it's the same as using any other Vault Economy plugin
     *
     * @param currenciesManager The CurrenciesManager instance
     * @param currencyName      The name of the currency
     * @param currencySingular  The singular symbol of the currency
     * @param currencyPlural    The plural symbol of the currency
     * @param startingBalance   The starting balance of the currency
     * @param transactionTax    The transaction tax of the currency
     */
    public Currency(CurrenciesManager currenciesManager, String currencyName, String currencySingular, String currencyPlural, double startingBalance, double transactionTax) {
        this.currenciesManager = currenciesManager;
        this.enabled = true;
        this.currencyName = currencyName;
        this.currencySingular = currencySingular;
        this.currencyPlural = currencyPlural;
        this.startingBalance = startingBalance;
        this.transactionTax = transactionTax;
        this.accounts = new ConcurrentHashMap<>();
        getOrderedAccounts().join().forEach(t -> accounts.put(UUID.fromString(t.getElement()), t.getScore()));
        registerChannelListener();
    }


    @Override
    public boolean isEnabled() {
        return enabled;
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
        return 0;
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount) + (amount == 1 ? currencySingular : currencyPlural);
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
    public boolean hasAccount(String playerName) {
        return accounts.containsKey(currenciesManager.getUUIDFromUsernameCache(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return accounts.containsKey(player.getUniqueId());
    }


    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }


    @Override
    public double getBalance(String playerName) {
        return accounts.get(currenciesManager.getUUIDFromUsernameCache(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return accounts.get(player.getUniqueId());
    }


    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }


    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }


    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        if (!hasAccount(playerName))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (amount * transactionTax);
        if (!has(playerName, amountToWithdraw))
            return new EconomyResponse(0, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        updateAccount(currenciesManager.getUUIDFromUsernameCache(playerName), playerName, getBalance(playerName) - amountToWithdraw);


        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!hasAccount(player))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        double amountToWithdraw = amount + (amount * transactionTax);
        if (!has(player, amountToWithdraw))
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        updateAccount(player.getUniqueId(), player.getName(), getBalance(player) - amountToWithdraw);


        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);

    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    /**
     * Set the balance of a player
     *
     * @param player The player to set the balance of
     * @param amount The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(OfflinePlayer player, double amount) {
        updateAccount(player.getUniqueId(), player.getName(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    /**
     * Set the balance of a player
     *
     * @param playerName The player to set the balance of
     * @param amount     The amount to set the balance to
     * @return The result of the operation
     */
    public EconomyResponse setPlayerBalance(String playerName, double amount) {
        updateAccount(currenciesManager.getUUIDFromUsernameCache(playerName), playerName, amount);
        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (!hasAccount(playerName))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccount(currenciesManager.getUUIDFromUsernameCache(playerName), playerName, getBalance(playerName) + amount);
        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!hasAccount(player))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccount(player.getUniqueId(), player.getName(), getBalance(player) + amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }


    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
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
    public boolean createPlayerAccount(String playerName) {
        if (hasAccount(playerName))
            return false;
        updateAccount(currenciesManager.getUUIDFromUsernameCache(playerName), playerName, startingBalance);
        return true;
    }


    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player))
            return false;
        updateAccount(player.getUniqueId(), player.getName(), startingBalance);

        return true;
    }


    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    void updateAccountLocal(UUID uuid, String playerName, double balance) {
        currenciesManager.updateNameUniqueId(playerName, uuid);
        accounts.put(uuid, balance);
    }

    private void updateAccount(UUID uuid, String playerName, double balance) {
        updateAccountCloudCache(uuid, playerName, balance, 0);
        updateAccountLocal(uuid, playerName, balance);
        //Update cache in other servers directly
        currenciesManager.getEzRedisMessenger().sendObjectPacket("rediseco_" + currencyName, new UpdateAccount(RedisEconomyPlugin.settings().SERVER_ID, uuid.toString(), playerName, balance));
    }

    private void updateAccountCloudCache(UUID uuid, String playerName, double balance, int tries) {
        currenciesManager.getEzRedisMessenger().jedisResourceFuture(jedis -> {
            Pipeline p = jedis.pipelined();
            p.zadd("rediseco:balances_" + currencyName, balance, uuid.toString());
            p.hset("rediseco:nameuuid", playerName, uuid.toString());
            p.syncAndReturnAll();
            return null;
        }, 300).exceptionally(e -> {
            if (tries > 4) {
                e.printStackTrace();
                Bukkit.getLogger().severe("Failed to update account " + playerName + " after 5 tries");
                Bukkit.getLogger().severe("Player accounts are desyncronized");
            } else updateAccountCloudCache(uuid, playerName, balance, tries + 1);
            return null;
        });

    }

    /**
     * Get ordered accounts from Redis
     * Redis uses ordered sets as data structures
     * This method has a time complexity of O(log(N)+M) with N being the number of elements in the sorted set and M the number of elements returned.
     *
     * @return A list of accounts ordered by balance in Tuples of UUID and balance (UUID is stringified)
     */
    public CompletableFuture<List<Tuple>> getOrderedAccounts() {
        return currenciesManager.getEzRedisMessenger().jedisResourceFuture(jedis -> jedis.zrevrangeWithScores("rediseco:balances_" + currencyName, 0, -1));
    }

    /**
     * Get single ordered account from Redis
     *
     * @param uuid The UUID of the account
     * @return The balance associated with the UUID on Redis
     */
    public CompletableFuture<Double> getAccountRedis(UUID uuid) {
        return currenciesManager.getEzRedisMessenger().jedisResourceFuture(jedis -> jedis.zscore("rediseco:balances_" + currencyName, uuid.toString()));
    }

    private void registerChannelListener() {
        currenciesManager.getEzRedisMessenger().registerChannelObjectListener("rediseco_" + currencyName, (packet) -> {
            Currency.UpdateAccount ua = (Currency.UpdateAccount) packet;

            if (ua.sender().equals(RedisEconomyPlugin.settings().SERVER_ID)) return;

            updateAccountLocal(UUID.fromString(ua.uuid()), ua.playerName(), ua.balance());

        }, Currency.UpdateAccount.class);
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

    public record UpdateAccount(String sender, String uuid, String playerName, double balance) implements Serializable {
    }
}
