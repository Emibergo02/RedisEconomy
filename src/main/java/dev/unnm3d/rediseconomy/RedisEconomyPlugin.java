package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.command.balance.BalanceCommand;
import dev.unnm3d.rediseconomy.command.balance.BalanceSubCommands;
import dev.unnm3d.rediseconomy.command.balance.BalanceTopCommand;
import dev.unnm3d.rediseconomy.command.transaction.ArchiveTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.TransactionCommand;
import dev.unnm3d.rediseconomy.command.transaction.BrowseTransactionsCommand;
import dev.unnm3d.rediseconomy.config.ConfigManager;
import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.config.Settings;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.utils.AdventureWebuiEditorAPI;
import dev.unnm3d.rediseconomy.utils.Metrics;
import dev.unnm3d.rediseconomy.utils.PlaceholderAPIHook;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.Getter;
import lombok.SneakyThrows;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    //private EzRedisMessenger ezRedisMessenger;
    @Getter
    private ConfigManager configManager;
    @Getter
    private CurrenciesManager currenciesManager;
    private RedisManager redisManager;


    public Settings settings() {
        return configManager.getSettings();
    }

    public Langs langs() {
        return configManager.getLangs();
    }

    public static RedisEconomyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        this.configManager = new ConfigManager(this);

        if (!setupRedis()) {
            this.getLogger().severe("Disabling: redis server unreachable!");
            this.getLogger().severe("Please setup a redis server before running this plugin!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            this.getLogger().info("Redis server connected!");
        }

        if (!setupVault()) { //creates currenciesManager and exchange
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.getLogger().info("Hooked into Vault!");
        }
    }

    @SneakyThrows
    @Override
    public void onEnable() {
        this.configManager.postStartupLoad();
        if (settings().migrationEnabled) {
            getServer().getScheduler().runTaskLaterAsynchronously(this, () ->
                            currenciesManager.getCompleteMigration().complete(null),
                    500L);//load: STARTUP doesn't consider dependencies on load so i have to wait a bit (bukkit bug?)
        }

        getServer().getPluginManager().registerEvents(currenciesManager, this);
        //Commands
        PayCommand payCommand = new PayCommand(currenciesManager, this);
        loadCommand("pay", payCommand, payCommand);
        BlockPaymentsCommand blockPaymentsCommand = new BlockPaymentsCommand(this);
        loadCommand("toggle-payments", blockPaymentsCommand, blockPaymentsCommand);
        BalanceCommand balanceCommand = new BalanceSubCommands(currenciesManager, this);
        loadCommand("balance", balanceCommand, balanceCommand);
        BalanceTopCommand balanceTopCommand = new BalanceTopCommand(currenciesManager, this);
        loadCommand("balancetop", balanceTopCommand, balanceTopCommand);
        TransactionCommand transactionCommand = new TransactionCommand(this);
        loadCommand("transaction", transactionCommand, transactionCommand);
        BrowseTransactionsCommand browseTransactionsCommand = new BrowseTransactionsCommand(this);
        loadCommand("browse-transactions", browseTransactionsCommand, browseTransactionsCommand);
        ArchiveTransactionsCommand archiveTransactionsCommand = new ArchiveTransactionsCommand(this);
        loadCommand("archive-transactions", archiveTransactionsCommand, archiveTransactionsCommand);
        PurgeUserCommand purgeUserCommand = new PurgeUserCommand(currenciesManager, this);
        loadCommand("purge-balance", purgeUserCommand, purgeUserCommand);
        SwitchCurrencyCommand switchCurrencyCommand = new SwitchCurrencyCommand(currenciesManager, this);
        loadCommand("switch-currency", switchCurrencyCommand, switchCurrencyCommand);
        BackupRestoreCommand backupRestoreCommand = new BackupRestoreCommand(currenciesManager, this);
        loadCommand("backup-economy", backupRestoreCommand, backupRestoreCommand);
        MainCommand mainCommand = new MainCommand(this, new AdventureWebuiEditorAPI(settings().webEditorUrl));
        loadCommand("rediseconomy", mainCommand, mainCommand);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
        }

        new Metrics(this, 16802);
    }

    @Override
    public void onDisable() {
        redisManager.close();
        if (currenciesManager != null)
            this.getServer().getServicesManager().unregister(Economy.class, currenciesManager.getDefaultCurrency());
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getLogger().info("RedisEconomy disabled successfully!");
    }

    private boolean setupRedis() {
        try {
            RedisURI.Builder redisURIBuilder = RedisURI.builder()
                    .withHost(configManager.getSettings().redis.getHost())
                    .withPort(configManager.getSettings().redis.getPort())
                    .withDatabase(configManager.getSettings().redis.getDatabase())
                    .withTimeout(Duration.of(configManager.getSettings().redis.getTimeout(), ChronoUnit.MILLIS))
                    .withClientName(configManager.getSettings().redis.getClientName());
            if (configManager.getSettings().redis.getUser().equals("changecredentials"))
                getLogger().warning("You are using default redis credentials. Please change them in the config.yml file!");
            //Authentication params
            redisURIBuilder = configManager.getSettings().redis.getPassword().equals("") ?
                    redisURIBuilder :
                    configManager.getSettings().redis.getUser().equals("") ?
                            redisURIBuilder.withPassword(configManager.getSettings().redis.getPassword().toCharArray()) :
                            redisURIBuilder.withAuthentication(configManager.getSettings().redis.getUser(), configManager.getSettings().redis.getPassword());

            getLogger().info("Connecting to redis server " + redisURIBuilder.build().toString() + "...");
            this.redisManager = new RedisManager(RedisClient.create(redisURIBuilder.build()));
            redisManager.isConnected().get(1, java.util.concurrent.TimeUnit.SECONDS);
            if (!configManager.getSettings().clusterId.equals(""))
                RedisKeys.setClusterId(configManager.getSettings().clusterId);
            return true;
        } catch (Exception e) {
            if (e.getMessage().contains("max number of clients reached")) {
                getLogger().severe("CHECK YOUR REDIS CREDENTIALS. DO NOT USE DEFAULT CREDENTIALS OR THE PLUGIN WILL LOSE DATA AND MAY NOT WORK!");
            } else
                e.printStackTrace();
            return false;
        }
    }

    private boolean setupVault() {
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
