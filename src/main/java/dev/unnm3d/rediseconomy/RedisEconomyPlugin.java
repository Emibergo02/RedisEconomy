package dev.unnm3d.rediseconomy;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.command.balance.BalanceCommand;
import dev.unnm3d.rediseconomy.command.balance.BalanceSubCommands;
import dev.unnm3d.rediseconomy.command.balance.BalanceTopCommand;
import dev.unnm3d.rediseconomy.command.transaction.ArchiveTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.BrowseTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.LastTransactionsCommand;
import dev.unnm3d.rediseconomy.command.transaction.TransactionCommand;
import dev.unnm3d.rediseconomy.config.ConfigManager;
import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.config.Settings;
import dev.unnm3d.rediseconomy.config.Settings.StorageType;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.migrators.*;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.storage.FileStorageService;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RedisEconomyPlugin extends JavaPlugin {

    @Getter
    private static RedisEconomyPlugin instance;
    @Getter
    private static UUID instanceUUID;
    @Getter
    private ConfigManager configManager;
    @Getter
    private CurrenciesManager currenciesManager;
    private RedisManager redisManager;
    @Getter
    private FileStorageService fileStorageService;
    @Nullable
    @Getter
    private PlayerListManager playerListManager;
    @Getter
    private TaskScheduler scheduler;
    @Getter
    private Plugin vaultPlugin;
    @Getter
    private File debugFile;


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

        if (settings().storageType == StorageType.FILE) {
            getDataFolder().mkdirs();
            this.fileStorageService = new FileStorageService(this);
            getLogger().info("File storage mode enabled. Redis features are disabled.");
        } else if (!setupRedis()) {
            this.getLogger().severe("Disabling: redis server unreachable!");
            this.getLogger().severe("Please setup a redis server before running this plugin!");
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.getLogger().info("Redis server connected!");
        }
    }

    @Override
    public void onEnable() {
        if (settings().storageType == StorageType.REDIS && redisManager == null) return;
        loadDebugFile();

        this.scheduler = UniversalScheduler.getScheduler(this);
        this.configManager.loadLangs();
        this.vaultPlugin = getServer().getPluginManager().getPlugin("Vault");
        if (this.vaultPlugin == null) { //creates currenciesManager and exchange
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadPlayerList();

        this.currenciesManager = new CurrenciesManager(redisManager, fileStorageService, this, configManager);
        this.getLogger().info("Hooked into Vault!");

        if (settings().migrationEnabled) {
            scheduler.runTaskLater(() -> {
                Migrator migrator;
                if (getServer().getPluginManager().getPlugin("XConomy") != null) {
                    migrator = new XConomyMigrator();
                } else if (getServer().getPluginManager().getPlugin("Essentials") != null) {
                    migrator = new EssentialsMigrator(getServer().getPluginManager().getPlugin("Essentials"));
                } else if (getServer().getPluginManager().getPlugin("CMI") != null) {
                    migrator = new CMIMigrator(getServer().getPluginManager().getPlugin("CMI"));
                } else {
                    migrator = new OfflinePlayersMigrator(this);
                }
                migrator.migrate(currenciesManager.getDefaultCurrency());

                getLogger().info("§aMigration completed!");
                getLogger().info("§aRestart the server to apply the changes.");

                configManager.getSettings().migrationEnabled = false;
                configManager.saveConfigs();
            }, 100L);
        }

        getServer().getPluginManager().registerEvents(currenciesManager, this);
        //Commands
        PayCommand payCommand = new PayCommand(currenciesManager, this);
        loadCommand("pay", payCommand, payCommand);
        TogglePaymentsCommand togglePaymentsCommand = new TogglePaymentsCommand(this);
        loadCommand("toggle-payments", togglePaymentsCommand, togglePaymentsCommand);
        BalanceCommand balanceCommand = new BalanceSubCommands(currenciesManager, this);
        loadCommand("balance", balanceCommand, balanceCommand);
        BalanceTopCommand balanceTopCommand = new BalanceTopCommand(currenciesManager, this);
        loadCommand("balancetop", balanceTopCommand, balanceTopCommand);
        TransactionCommand transactionCommand = new TransactionCommand(this);
        loadCommand("transaction", transactionCommand, transactionCommand);
        BrowseTransactionsCommand browseTransactionsCommand = new BrowseTransactionsCommand(this);
        loadCommand("browse-transactions", browseTransactionsCommand, browseTransactionsCommand);
        LastTransactionsCommand lastTransactionsCommand = new LastTransactionsCommand(this);
        loadCommand("last-transactions", lastTransactionsCommand, lastTransactionsCommand);
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

        if (settings().storageType == StorageType.FILE && fileStorageService != null) {
            int intervalSeconds = Math.max(1, settings().fileSaveSeconds);
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> fileStorageService.saveSnapshot(currenciesManager),
                    intervalSeconds * 20L, intervalSeconds * 20L);
            fileStorageService.saveSnapshot(currenciesManager);
        }
    }

    @Override
    public void onDisable() {
        if (playerListManager != null)
            playerListManager.stop();
        if (redisManager != null)
            redisManager.close();
        if (currenciesManager != null) {
            this.getServer().getServicesManager().unregister(Economy.class, currenciesManager.getDefaultCurrency());
            currenciesManager.terminate();
        }
        if (settings().storageType == StorageType.FILE && fileStorageService != null && currenciesManager != null) {
            fileStorageService.saveSnapshot(currenciesManager);
        }
        getLogger().info("RedisEconomy disabled successfully!");
    }

    /**
     * Load the player list manager if the tabOnlinePlayers setting is enabled
     */
    public void loadPlayerList() {
        if (configManager.getSettings().tabOnlinePlayers && settings().storageType == StorageType.REDIS) {
            this.playerListManager = new PlayerListManager(this.redisManager, this);
        } else if (playerListManager != null) {
            playerListManager.stop();
            playerListManager = null;
        }
    }

    private boolean setupRedis() {
        try {
            RedisURI.Builder redisURIBuilder = RedisURI.builder()
                    .withHost(configManager.getSettings().redis.host())
                    .withPort(configManager.getSettings().redis.port())
                    .withDatabase(configManager.getSettings().redis.database())
                    .withSsl(configManager.getSettings().redis.ssl())
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
            this.redisManager = new RedisManager(RedisClient.create(redisURIBuilder.build()), configManager.getSettings().redis.getPoolSize());
            redisManager.isConnected().get(1, java.util.concurrent.TimeUnit.SECONDS);
            if (!configManager.getSettings().clusterId.isEmpty())
                RedisKeys.setClusterId(configManager.getSettings().clusterId);
            return true;
        } catch (Exception e) {
            if (e.getMessage().contains("max number of clients reached")) {
                getLogger().severe("CHECK YOUR REDIS CREDENTIALS. DO NOT USE DEFAULT CREDENTIALS OR THE PLUGIN WILL LOSE DATA AND MAY NOT WORK!");
            } else e.printStackTrace();
            return false;
        }
    }

    public boolean isFileStorage() {
        return settings().storageType == StorageType.FILE;
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void loadDebugFile() {
        final File logsDir = new File(getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdir();
        }
        debugFile = new File(logsDir, "debug" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
        if (!debugFile.exists()) {
            try {
                debugFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void debug(String string) {
        if (RedisEconomyPlugin.getInstance().settings().debug) {
            try {
                final FileWriter writer = new FileWriter(RedisEconomyPlugin.getInstance().getDebugFile().getAbsoluteFile(), true);
                writer.append("[")
                        .append(String.valueOf(LocalDateTime.now()))
                        .append("] ")
                        .append(string);

                writer.append("\r\n");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void debugCache(String string) {
        if (RedisEconomyPlugin.getInstance().settings().debugUpdateCache) {
            try {
                final FileWriter writer = new FileWriter(RedisEconomyPlugin.getInstance().getDebugFile().getAbsoluteFile(), true);
                writer.append("[")
                        .append(String.valueOf(LocalDateTime.now()))
                        .append("] CACHE: ")
                        .append(string);

                writer.append("\r\n");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
