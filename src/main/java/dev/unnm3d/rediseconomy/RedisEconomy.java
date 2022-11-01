package dev.unnm3d.rediseconomy;

import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.jedis.Pipeline;
import dev.unnm3d.jedis.resps.Tuple;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
@Setter
@Getter
public class RedisEconomy implements Economy {

    private EzRedisMessenger ezRedisMessenger;
    private boolean enabled;
    private String currencySingular;
    private String currencyPlural;
    private HashMap<UUID, Double> accounts;
    private Map<String, UUID> nameUniqueIds;

    public RedisEconomy(EzRedisMessenger ezRedisMessenger, String currencySingular, String currencyPlural) {
        this.ezRedisMessenger = ezRedisMessenger;
        this.enabled = true;
        this.currencySingular = currencySingular;
        this.currencyPlural = currencyPlural;
        this.accounts = new HashMap<>();
        getAccountsRedis().join().forEach(t -> accounts.put(UUID.fromString(t.getElement()), t.getScore()));
        this.nameUniqueIds = getRedisNameUniqueIds().join();
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
        return String.format("%.2f", amount);
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
        return accounts.containsKey(nameUniqueIds.get(playerName));
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
        return accounts.get(nameUniqueIds.get(playerName));
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
        if (!has(playerName, amount))
            return new EconomyResponse(0, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        updateAccountStorage(nameUniqueIds.get(playerName), playerName, getBalance(playerName) - amount);


        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!hasAccount(player))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        if (!has(player, amount))
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        updateAccountStorage(player.getUniqueId(), player.getName(), getBalance(player) - amount);


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

    public EconomyResponse setPlayerBalance(OfflinePlayer player, double amount) {
        updateAccountStorage(player.getUniqueId(), player.getName(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    public EconomyResponse setPlayerBalance(String playerName, double amount) {
        if (!hasAccount(playerName))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccountStorage(nameUniqueIds.get(playerName), playerName, amount);
        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (!hasAccount(playerName))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccountStorage(nameUniqueIds.get(playerName), playerName, getBalance(playerName) + amount);
        return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!hasAccount(player))
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        updateAccountStorage(player.getUniqueId(), player.getName(), getBalance(player) + amount);
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
        updateAccountStorage(nameUniqueIds.get(playerName), playerName, 0.0);
        return true;
    }


    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player))
            return false;
        updateAccountStorage(player.getUniqueId(), player.getName(), 0.0);

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

    public void updateAccountLocal(UUID uuid, String playerName, double balance) {
        accounts.put(uuid, balance);
        nameUniqueIds.put(playerName, uuid);
    }

    public void updateAccountStorage(UUID uuid, String playerName, double balance) {
        ezRedisMessenger.jedisResourceFuture(jedis -> {
            Pipeline p = jedis.pipelined();
            if (balance == 0.0D) p.zincrby("rediseco:balances", balance, uuid.toString());
            else p.zadd("rediseco:balances", balance, uuid.toString());
            p.hset("rediseco:nameuuid", playerName, uuid.toString());
            p.syncAndReturnAll();
            return null;
        }).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });
        updateAccountLocal(uuid, playerName, balance);
        ezRedisMessenger.sendObjectPacketAsync("rediseco", new UpdateAccount(RedisEconomyPlugin.settings().SERVER_ID, uuid.toString(), playerName, balance));
    }

    public CompletableFuture<List<Tuple>> getAccountsRedis() {
        return ezRedisMessenger.jedisResourceFuture(jedis -> jedis.zrevrangeWithScores("rediseco:balances", 0, -1));
    }

    public CompletableFuture<Map<String, UUID>> getRedisNameUniqueIds() {
        return ezRedisMessenger.jedisResourceFuture(jedis -> {
            Map<String, UUID> nameUuids = new HashMap<>();
            jedis.hgetAll("rediseco:nameuuid").forEach((name, uuid) -> nameUuids.put(name, UUID.fromString(uuid)));
            return nameUuids;
        });
    }

    //With for loop
    public String getPlayerName(UUID uuid) {
        for (Map.Entry<String, UUID> entry : nameUniqueIds.entrySet()) {
            if (entry.getValue().equals(uuid))
                return entry.getKey();
        }
        return "N/A";
    }


    public record UpdateAccount(String sender, String uuid, String playerName, double balance) implements Serializable {
    }
}
