package dev.unnm3d.rediseconomy.currency;

import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;


public class CurrenciesManager {
    private final RedisEconomyPlugin plugin;
    private final EzRedisMessenger ezRedisMessenger;
    private final HashMap<String,Currency> currencies;

    public CurrenciesManager(EzRedisMessenger ezRedisMessenger,RedisEconomyPlugin plugin) {
        this.ezRedisMessenger = ezRedisMessenger;
        this.plugin = plugin;
        this.currencies = new HashMap<>();
        ConfigurationSection configurationSection=plugin.getConfig().getConfigurationSection("currencies");
        for(String key:configurationSection.getKeys(false)){
            String singleSymbol=configurationSection.getString(key+".currency-single","€");
            String pluralSymbol=configurationSection.getString(key+".currency-plural","€");
            double starting=configurationSection.getDouble(key+".starting-balance",0.0);
            double tax=configurationSection.getDouble(key+".pay-tax",0.0);
            System.out.println("Loaded currency "+key+" with single symbol "+singleSymbol+" and plural symbol "+pluralSymbol+" and starting balance "+starting+" and tax "+tax);
            Currency currency=new Currency(ezRedisMessenger,key,singleSymbol,pluralSymbol,starting,tax);
            currencies.put(key,currency);
        }
        if(currencies.get("vault")==null){
            currencies.put("vault",new Currency(ezRedisMessenger,"vault","€","€",0.0,0.0));
        }
    }


    public boolean loadDefaultCurrency() {
        Currency defaultCurrency= currencies.get("vault");
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null)
            return false;
        if (plugin.getConfig().getBoolean("migration-enabled", false)) {

            @NotNull Collection<RegisteredServiceProvider<Economy>> existentProviders = plugin.getServer().getServicesManager().getRegistrations(Economy.class);
            CompletableFuture.supplyAsync(() -> {
                Bukkit.getLogger().info("§aStarting migration from " + existentProviders.size() + " providers...");

                if (ezRedisMessenger.getJedis().isConnected())
                    existentProviders.forEach(reg -> {
                        Bukkit.getLogger().info("§aMigrating from " + reg.getProvider().getName() + "...");
                        if (reg.getProvider() != defaultCurrency) {
                            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                double bal = reg.getProvider().getBalance(offlinePlayer);
                                defaultCurrency.setPlayerBalance(offlinePlayer, bal);
                                Bukkit.getLogger().info("Migrated " + offlinePlayer.getName() + "'s balance of " + bal);
                            }

                        }
                    });
                Bukkit.getLogger().info("§aMigration finished");
                return defaultCurrency;
            }).thenAccept((vaultCurrency) -> {
                plugin.getServer().getServicesManager().register(Economy.class, vaultCurrency, vault, ServicePriority.High);
                plugin.getConfig().set("migration-enabled", false);
                plugin.saveConfig();
            });
        } else
            plugin.getServer().getServicesManager().register(Economy.class, defaultCurrency, vault, ServicePriority.High);
        return true;
    }
    public Currency getCurrency(String name){
        return currencies.get(name);
    }
    public Collection<Currency> getCurrencies(){
        return currencies.values();
    }
    public Currency getDefaultCurrency(){
        return currencies.get("vault");
    }
}
