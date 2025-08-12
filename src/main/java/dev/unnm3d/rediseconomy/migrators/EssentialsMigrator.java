package dev.unnm3d.rediseconomy.migrators;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import org.bukkit.plugin.Plugin;

import java.util.*;


public class EssentialsMigrator implements Migrator {

    private final Essentials essentialsPlugin;

    public EssentialsMigrator(Plugin essentialsPlugin) {
        this.essentialsPlugin = (Essentials) essentialsPlugin;
    }

    @Override
    public void migrate(Currency defaultCurrency) {

        RedisEconomyPlugin.getInstance().getLogger().info("§aMigrating from Essentials...");

        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();

        int i = 0;
        for (UUID userUUID : essentialsPlugin.getUsers().getAllUserUUIDs()) {
            try {
                User user = essentialsPlugin.getUsers().getUser(userUUID);
                balances.add(ScoredValue.just(user.getMoney().doubleValue(), userUUID.toString()));
                if (user.getName() != null && !user.getName().isEmpty()) {
                    nameUniqueIds.put(user.getName(), userUUID.toString());
                }
                defaultCurrency.updateAccountLocal(userUUID, user.getName() == null ? userUUID.toString() : user.getName(), user.getMoney().doubleValue());
                i++;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (i % 100 == 0) {
                RedisEconomyPlugin.getInstance().getLogger().info("Progress: " + i + "/" + essentialsPlugin.getUsers().getAllUserUUIDs().size());
            }
        }

        defaultCurrency.updateBulkAccountsCloudCache(balances, nameUniqueIds);
    }
}
