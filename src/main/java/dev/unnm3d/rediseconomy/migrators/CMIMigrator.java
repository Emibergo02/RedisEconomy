package dev.unnm3d.rediseconomy.migrators;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CMIMigrator implements Migrator {

    private final CMI cmiPlugin;

    public CMIMigrator(Plugin cmiPlugin) {
        this.cmiPlugin = (CMI) cmiPlugin;
    }

    @Override
    public void migrate(Currency defaultCurrency) {

        RedisEconomyPlugin.getInstance().getLogger().info("§aMigrating from CMI...");

        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();

        int i = 0;
        for (CMIUser user : cmiPlugin.getPlayerManager().getAllUsers().values()) {
            try {

                balances.add(ScoredValue.just(user.getBalance(), user.getUniqueId().toString()));
                if (user.getName() != null && !user.getName().isEmpty()) {
                    nameUniqueIds.put(user.getName(), user.getUniqueId().toString());
                }
                defaultCurrency.updateAccountLocal(user.getUniqueId(), user.getName() == null ? user.getUniqueId().toString() : user.getName(), user.getBalance());
                i++;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (i % 100 == 0) {
                RedisEconomyPlugin.getInstance().getLogger().info("Progress: " + i + "/" + cmiPlugin.getPlayerManager().getAllUsers().size());
            }
        }

        defaultCurrency.updateBulkAccountsCloudCache(balances, nameUniqueIds);
    }
}
