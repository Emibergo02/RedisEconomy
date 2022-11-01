package dev.unnm3d.rediseconomy;

import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.rediseconomy.command.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    private static RedisEconomy economy;
    private EzRedisMessenger ezRedisMessenger;
    private Settings settings;

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.settings = new Settings(this);

        if (!setupRedis()) {
            this.getLogger().severe("Disabled: redis server unreachable!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else this.getLogger().info("Hooked into Vault!");

        registerRedisChannels();

        EconomyExchange exchange = new EconomyExchange(economy);


        getServer().getPluginManager().registerEvents(new JoinListener(economy), this);
        getServer().getPluginCommand("pay").setExecutor(new PayCommand(economy, exchange));
        getServer().getPluginCommand("pay").setTabCompleter(new PayCommand(economy, exchange));
        getServer().getPluginCommand("balance").setExecutor(new BalanceCommand(economy));
        getServer().getPluginCommand("balance").setTabCompleter(new BalanceCommand(economy));
        getServer().getPluginCommand("balancetop").setExecutor(new BalanceTopCommand(economy));
        getServer().getPluginCommand("transaction").setExecutor(new TransactionCommand(economy, exchange));
        getServer().getPluginCommand("transaction").setTabCompleter(new TransactionCommand(economy, exchange));


    }

    @Override
    public void onDisable() {
        economy.getEzRedisMessenger().destroy();
        this.getServer().getServicesManager().unregister(Economy.class, economy);
    }

    private void registerRedisChannels() {
        ezRedisMessenger.registerChannelObjectListener("rediseco", (packet) -> {
            RedisEconomy.UpdateAccount ua = (RedisEconomy.UpdateAccount) packet;

            if (ua.sender().equals(settings.SERVER_ID)) return;

            economy.updateAccountLocal(UUID.fromString(ua.uuid()), ua.playerName(), ua.balance());

        }, RedisEconomy.UpdateAccount.class);
        ezRedisMessenger.registerChannelObjectListener("rediseco:paymsg", (packet) -> {
            PayCommand.PayMsg payMsgPacket = (PayCommand.PayMsg) packet;
            Player online = getServer().getPlayer(payMsgPacket.receiverName());
            if (online != null) {
                if (online.isOnline())
                    settings.send(online,settings.PAY_RECEIVED.replace("%player%", payMsgPacket.sender()).replace("%amount%", payMsgPacket.amount()));
            }
        }, PayCommand.PayMsg.class);
    }


    private boolean setupRedis() {
        try {
            this.ezRedisMessenger = new EzRedisMessenger(
                    getConfig().getString("redis.host", "localhost"),
                    getConfig().getInt("redis.port", 6379),
                    getConfig().getString("redis.user", "").equals("") ? null : getConfig().getString("redis.user"),
                    getConfig().getString("redis.password", "").equals("") ? null : getConfig().getString("redis.password"),
                    getConfig().getInt("redis.timeout", 0),
                    getConfig().getInt("redis.database", 0),
                    "RedisEconomy");
            return true;
        } catch (InstantiationException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean setupEconomy() {
        Plugin vault = this.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null)
            return false;
        economy = new RedisEconomy(ezRedisMessenger, getConfig().getString("currency-single", "coin"), getConfig().getString("currency-plural", "coins"));

        if(getConfig().getBoolean("migration-enabled", false)) {

            @NotNull Collection<RegisteredServiceProvider<Economy>> existentProviders= Bukkit.getServer().getServicesManager().getRegistrations(Economy.class);
            CompletableFuture.supplyAsync(() -> {
                Bukkit.getLogger().info("§aStarting migration from " + existentProviders.size() + " providers...");

                if(ezRedisMessenger.getJedis().isConnected())
                    existentProviders.forEach(reg -> {
                        Bukkit.getLogger().info("§aMigrating from " + reg.getProvider().getName() + "...");
                        if (reg.getProvider() != economy) {
                            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                double bal = reg.getProvider().getBalance(offlinePlayer);
                                economy.setPlayerBalance(offlinePlayer, bal);
                                Bukkit.getLogger().info("Migrated " + offlinePlayer.getName() + "'s balance of " + bal);
                            }

                        }
                    });
                Bukkit.getLogger().info("§aMigration finished");
                return economy;
            }).thenAccept((economy) -> {
                this.getServer().getServicesManager().register(Economy.class, economy, vault, ServicePriority.High);
                getConfig().set("migration-enabled", false);
                saveConfig();
            });
        }else
            this.getServer().getServicesManager().register(Economy.class, economy, vault, ServicePriority.High);


        return true;
    }

    public static RedisEconomy getEconomy() {
        return economy;
    }

    public static Settings settings() {
        return instance.settings;
    }



}
