package dev.unnm3d.rediseconomy;

import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.rediseconomy.command.BalanceCommand;
import dev.unnm3d.rediseconomy.command.BalanceTopCommand;
import dev.unnm3d.rediseconomy.command.PayCommand;
import dev.unnm3d.rediseconomy.command.TransactionCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    private static RedisEconomy economy;
    private EzRedisMessenger ezRedisMessenger;
    private Settings settings;

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.reloadConfig();
        this.settings = new Settings(this.getConfig());

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
                    online.sendMessage(settings.parse(settings.PAY_RECEIVED.replace("%player%", payMsgPacket.sender()).replace("%amount%", payMsgPacket.amount())));
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
        this.getServer().getServicesManager().register(Economy.class, economy, vault, ServicePriority.High);

        return true;
    }

    public static RedisEconomy getEconomy() {
        return economy;
    }

    public static FileConfiguration getConfiguration() {
        return instance.getConfig();
    }

    public static Settings settings() {
        return instance.settings;
    }

    @Override
    public void onDisable() {
        economy.getEzRedisMessenger().destroy();
        this.getServer().getServicesManager().unregister(Economy.class, economy);
    }

}
