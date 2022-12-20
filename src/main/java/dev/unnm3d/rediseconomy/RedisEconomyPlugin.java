package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.config.ConfigManager;
import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.config.Settings;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.utils.Metrics;
import dev.unnm3d.rediseconomy.utils.PlaceholderAPIHook;
import io.lettuce.core.RedisClient;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    //private EzRedisMessenger ezRedisMessenger;
    @Getter
    private static ConfigManager configManager;
    private CurrenciesManager currenciesManager;
    private RedisManager redisManager;


    public static Settings settings() {
        return configManager.getSettings();
    }

    public static Langs langs() {
        return configManager.getLangs();
    }

    @SuppressWarnings("unused")
    public static RedisEconomyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);

        if (!setupRedis()) {
            this.getLogger().severe("Disabled: redis server unreachable!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            this.getLogger().info("Redis server connected!");
        }

        if (!setupEconomy()) { //creates currenciesManager and exchange
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            this.getLogger().info("Hooked into Vault!");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(currenciesManager, configManager.getLangs()).register();
        }

        getServer().getPluginManager().registerEvents(currenciesManager, this);
        //Commands
        PayCommand payCommand = new PayCommand(currenciesManager);
        loadCommand("pay", payCommand, payCommand);
        BalanceCommand balanceCommand = new BalanceSubCommands(currenciesManager, this);
        loadCommand("balance", balanceCommand, balanceCommand);
        Objects.requireNonNull(getServer().getPluginCommand("balancetop")).setExecutor(new BalanceTopCommand(currenciesManager));
        TransactionCommand transactionCommand = new TransactionCommand(currenciesManager);
        loadCommand("transaction", transactionCommand, transactionCommand);
        BrowseTransactionsCommand browseTransactionsCommand = new BrowseTransactionsCommand(currenciesManager);
        loadCommand("browse-transactions", browseTransactionsCommand, browseTransactionsCommand);
        PurgeUserCommand purgeUserCommand = new PurgeUserCommand(currenciesManager);
        loadCommand("purge-balance", purgeUserCommand, purgeUserCommand);
        SwitchCurrencyCommand switchCurrencyCommand = new SwitchCurrencyCommand(currenciesManager);
        loadCommand("switch-currency", switchCurrencyCommand, switchCurrencyCommand);
        MainCommand mainCommand = new MainCommand(configManager);
        loadCommand("rediseconomy", mainCommand, mainCommand);

        new Metrics(this, 16802);
    }

    @Override
    public void onDisable() {
        //ezRedisMessenger.destroy();
        redisManager.close();
        this.getServer().getServicesManager().unregister(Economy.class, currenciesManager.getDefaultCurrency());
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getLogger().info("RedisEconomy disabled successfully!");
    }

    private boolean setupRedis() {
        this.redisManager = new RedisManager(RedisClient.create(configManager.getSettings().redisUri), configManager.getSettings().redisConnectionTimeout);
        getLogger().info("Connecting to redis server " + configManager.getSettings().redisUri + "...");
        return redisManager.isConnected();
    }

    private boolean setupEconomy() {
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null)
            return false;
        this.currenciesManager = new CurrenciesManager(redisManager, this, configManager);
        currenciesManager.loadDefaultCurrency(vault);
        return true;
    }

    private void loadCommand(String cmdName, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand cmd = getServer().getPluginCommand(cmdName);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(tabCompleter);
        } else {
            getLogger().warning("Command " + cmdName + " not found!");
        }
    }

}
