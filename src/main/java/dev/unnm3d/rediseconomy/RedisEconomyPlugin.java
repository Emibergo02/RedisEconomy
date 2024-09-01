package dev.unnm3d.rediseconomy;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.command.balance.BalanceCommand;
import dev.unnm3d.rediseconomy.command.balance.BalanceSubCommands;
import dev.unnm3d.rediseconomy.command.balance.BalanceTopCommand;
import dev.unnm3d.rediseconomy.command.transaction.ArchiveTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.BrowseTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.TransactionCommand;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RedisEconomyPlugin extends JavaPlugin {

    @Getter
    private static RedisEconomyPlugin instance;
    @Getter
    private ConfigManager configManager;
    @Getter
    private CurrenciesManager currenciesManager;
    private RedisManager redisManager;
    @Getter
    private TaskScheduler scheduler;
    @Getter
    private Plugin vaultPlugin;
    @Getter
    private static UUID instanceUUID;


    public Settings settings() {
        return configManager.getSettings();
    }

    public Langs langs() {
        return configManager.getLangs();
    }

    @Override
    public void onLoad() {
        instance = this;
        //Generate a unique instance id to not send redis updates to itself
        instanceUUID = UUID.randomUUID();

        this.configManager = new ConfigManager(this);

        if (!setupRedis()) {
            this.getLogger().severe("Disabling: redis server unreachable!");
            this.getLogger().severe("Please setup a redis server before running this plugin!");
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.getLogger().info("Redis server connected!");
        }
    }

    @Override
    public void onEnable() {
        if (redisManager == null) return;

        this.scheduler = UniversalScheduler.getScheduler(this);
        this.configManager.loadLangs();
        this.vaultPlugin = getServer().getPluginManager().getPlugin("Vault");
        if (this.vaultPlugin == null) { //creates currenciesManager and exchange
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.currenciesManager = new CurrenciesManager(redisManager, this, configManager);
        this.getLogger().info("Hooked into Vault!");

        if (settings().migrationEnabled) {
            scheduler.runTaskLater(() ->
                    currenciesManager.migrateFromOfflinePlayers(getServer().getOfflinePlayers()), 100L);
        } else {
            currenciesManager.loadDefaultCurrency(this.vaultPlugin);
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
        if (redisManager != null)
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
                    .withHost(configManager.getSettings().redis.host())
                    .withPort(configManager.getSettings().redis.port())
                    .withDatabase(configManager.getSettings().redis.database())
                    .withTimeout(Duration.of(configManager.getSettings().redis.timeout(), TimeUnit.MILLISECONDS.toChronoUnit()))
                    .withClientName(configManager.getSettings().redis.clientName());
            if (configManager.getSettings().redis.user().equals("changecredentials"))
                getLogger().warning("You are using default redis credentials. Please change them in the config.yml file!");
            //Authentication params
            redisURIBuilder = configManager.getSettings().redis.password().isEmpty() ?
                    redisURIBuilder :
                    configManager.getSettings().redis.user().isEmpty() ?
                            redisURIBuilder.withPassword(configManager.getSettings().redis.password().toCharArray()) :
                            redisURIBuilder.withAuthentication(configManager.getSettings().redis.user(), configManager.getSettings().redis.password());

            getLogger().info("Connecting to redis server " + redisURIBuilder.build().toString() + "...");
            this.redisManager = new RedisManager(RedisClient.create(redisURIBuilder.build()), configManager.getSettings().redis.poolSize());
            redisManager.isConnected().get(1, java.util.concurrent.TimeUnit.SECONDS);
            if (!configManager.getSettings().clusterId.isEmpty())
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
