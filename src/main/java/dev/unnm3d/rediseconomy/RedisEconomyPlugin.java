package dev.unnm3d.rediseconomy;

import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.rediseconomy.command.BalanceCommand;
import dev.unnm3d.rediseconomy.command.BalanceTopCommand;
import dev.unnm3d.rediseconomy.command.PayCommand;
import dev.unnm3d.rediseconomy.command.TransactionCommand;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    private Currency defaultCurrency;
    private EzRedisMessenger ezRedisMessenger;
    private Settings settings;
    private CurrenciesManager currenciesManager;

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
        } else{
            this.getLogger().info("Hooked into Vault!");
            this.defaultCurrency =currenciesManager.getCurrency("vault");
        }

        registerRedisChannels();

        EconomyExchange exchange = new EconomyExchange(defaultCurrency);


        getServer().getPluginManager().registerEvents(new JoinListener(currenciesManager), this);
        PayCommand payCommand = new PayCommand(currenciesManager, exchange);
        getServer().getPluginCommand("pay").setExecutor(payCommand);
        getServer().getPluginCommand("pay").setTabCompleter(payCommand);
        BalanceCommand balanceCommand = new BalanceCommand(currenciesManager);
        getServer().getPluginCommand("balance").setExecutor(balanceCommand);
        getServer().getPluginCommand("balance").setTabCompleter(balanceCommand);
        getServer().getPluginCommand("balancetop").setExecutor(new BalanceTopCommand(defaultCurrency));
        TransactionCommand transactionCommand = new TransactionCommand(defaultCurrency, exchange);
        getServer().getPluginCommand("transaction").setExecutor(transactionCommand);
        getServer().getPluginCommand("transaction").setTabCompleter(transactionCommand);


    }

    @Override
    public void onDisable() {
        ezRedisMessenger.destroy();
        this.getServer().getServicesManager().unregister(Economy.class, defaultCurrency);
    }

    private void registerRedisChannels() {
        ezRedisMessenger.registerChannelObjectListener("rediseco:paymsg", (packet) -> {
            PayCommand.PayMsg payMsgPacket = (PayCommand.PayMsg) packet;
            Player online = getServer().getPlayer(payMsgPacket.receiverName());
            if (online != null) {
                if (online.isOnline())
                    settings.send(online, settings.PAY_RECEIVED.replace("%player%", payMsgPacket.sender()).replace("%amount%", payMsgPacket.amount()));
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
        this.currenciesManager = new CurrenciesManager(ezRedisMessenger,this);

        return currenciesManager.loadDefaultCurrency();
    }

    public Currency getDefaultCurrency() {
        return defaultCurrency;
    }

    public static Settings settings() {
        return instance.settings;
    }


}
