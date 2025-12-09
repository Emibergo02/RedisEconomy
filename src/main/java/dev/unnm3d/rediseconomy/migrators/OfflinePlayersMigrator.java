package dev.unnm3d.rediseconomy.migrators;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@AllArgsConstructor
public class OfflinePlayersMigrator implements Migrator {

    private final RedisEconomyPlugin plugin;

    @Override
    public void migrate(Currency defaultCurrency) {
        RegisteredServiceProvider<Economy> existentProvider = plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (existentProvider == null) {
            plugin.getLogger().severe("Vault economy provider not found!");
            return;
        }
        OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();

        plugin.getLogger().info("§aMigrating from " + existentProvider.getProvider().getName() + "...");

        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();

        for (int i = 0; i < offlinePlayers.length; i++) {
            final OfflinePlayer offlinePlayer = offlinePlayers[i];

            try {
                double bal = existentProvider.getProvider().getBalance(offlinePlayer);

                balances.add(ScoredValue.just(bal, offlinePlayer.getUniqueId().toString()));
                if (offlinePlayer.getName() != null) {
                    nameUniqueIds.put(offlinePlayer.getName(), offlinePlayer.getUniqueId().toString());
                }

                defaultCurrency.updateAccountLocal(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName(), bal);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (i % 100 == 0) {
                plugin.getLogger().info("Progress: " + i + "/" + offlinePlayers.length);
            }
        }

        defaultCurrency.getEconomyStorage().updateBulkAccounts(defaultCurrency.getCurrencyName(), balances, nameUniqueIds);
    }
}
