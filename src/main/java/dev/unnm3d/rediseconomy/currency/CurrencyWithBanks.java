package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.*;

public class CurrencyWithBanks extends Currency{
    private final ConcurrentHashMap<String, Double> accounts;
    private final ConcurrentHashMap<String, List<UUID>> memberships;
    private final ConcurrentHashMap<String, UUID> owners;
    public CurrencyWithBanks(CurrenciesManager currenciesManager, String currencyName, String currencySingular, String currencyPlural, double startingBalance, double transactionTax) {
        super(currenciesManager, currencyName, currencySingular, currencyPlural, startingBalance, transactionTax);
        accounts = new ConcurrentHashMap<>();
        memberships = new ConcurrentHashMap<>();
        owners = new ConcurrentHashMap<>();
    }

    @Override
    public boolean hasBankSupport() {
        return true;
    }
    @Override
    public EconomyResponse createBank(@NotNull String accountId, String player) {
        updateBankAccount(accountId, 0);
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse createBank(@NotNull String accountId, OfflinePlayer player) {
        updateBankAccount(accountId, 0);
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse deleteBank(@NotNull String accountId) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankBalance(@NotNull String accountId) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankHas(@NotNull String accountId, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankWithdraw(@NotNull String accountId, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse bankDeposit(@NotNull String accountId, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String accountId, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
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
        return List.of();
    }
    private void setOwner(@NotNull String accountId, UUID ownerUUID){
        currenciesManager.getRedisManager().getConnection(connection -> {

            connection.async().hset(BANK_OWNERS.toString(), accountId, ownerUUID.toString());

            return null;
        });
    }

    void updateBankAccountLocal(String accountId, double balance) {
        accounts.put(accountId, balance);
    }

    private void updateBankAccount(@NotNull String accountId, double balance) {
        updateBankAccountCloudCache(accountId, balance, 0);
        updateBankAccountLocal(accountId, balance);
    }

    private void updateBankAccountCloudCache(@NotNull String accountId, double balance, int tries) {
        try {
            currenciesManager.getRedisManager().getConnection(connection -> {
                connection.setTimeout(Duration.ofMillis(300));
                RedisAsyncCommands<String, String> commands = connection.async();
                connection.setAutoFlushCommands(false);
                commands.zadd(BALANCE_BANK_PREFIX + currencyName, balance, accountId);
                commands.publish(UPDATE_BANK_CHANNEL_PREFIX + currencyName, RedisEconomyPlugin.getInstance().settings().serverId + ";;" + accountId + ";;" + balance).thenAccept((result) -> {
                    if (RedisEconomyPlugin.getInstance().settings().debug) {
                        Bukkit.getLogger().info("01 Sent bank update account " + accountId + " to " + balance);
                    }
                });
                connection.flushCommands();
                return null;
            });

        } catch (Exception e) {
            if (tries < 3) {
                e.printStackTrace();
                Bukkit.getLogger().severe("Failed to update bank account " + accountId + " after 3 tries");
                Bukkit.getLogger().severe("Bank accounts are desynchronized");
                updateBankAccountCloudCache(accountId, balance, tries + 1);
            } else {
                e.printStackTrace();
            }
        }

    }



}
