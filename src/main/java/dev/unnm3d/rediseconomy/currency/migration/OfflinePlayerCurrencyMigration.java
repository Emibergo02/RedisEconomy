package dev.unnm3d.rediseconomy.currency.migration;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.currency.CurrencyMigration;
import io.lettuce.core.ScoredValue;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfflinePlayerCurrencyMigration extends CurrencyMigration {

    RegisteredServiceProvider<Economy> existentProvider;

    public OfflinePlayerCurrencyMigration(RedisEconomyPlugin plugin, Currency currency) {
        super(plugin, currency);
    }

    @Override
    public String getProvider() {
        return existentProvider.getProvider().getName();
    }

    @Override
    protected boolean setup() {
        existentProvider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (existentProvider == null) {
            plugin.getLogger().severe("Vault economy provider not found!");
            return false;
        }
        return true;
    }

    @Override
    protected void start() {
        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();

        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();
        for (int i = 0; i < offlinePlayers.length; i++) {
            final OfflinePlayer offlinePlayer = offlinePlayers[i];
            try {
                double bal = existentProvider.getProvider().getBalance(offlinePlayer);
                balances.add(ScoredValue.just(bal, offlinePlayer.getUniqueId().toString()));
                if (offlinePlayer.getName() != null)
                    nameUniqueIds.put(offlinePlayer.getName(), offlinePlayer.getUniqueId().toString());
                currency.updateAccountLocal(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName(), bal);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (i % 1000 == 0) {
                plugin.getLogger().info("Progress: " + i + "/" + offlinePlayers.length);
            }
        }
        currency.updateBulkAccountsCloudCache(balances, nameUniqueIds);
    }
}
