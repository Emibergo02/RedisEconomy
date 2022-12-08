package dev.unnm3d.rediseconomy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import io.lettuce.core.RedisClient;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    //private EzRedisMessenger ezRedisMessenger;
    @Getter
    private Settings settings;
    private CurrenciesManager currenciesManager;
    private RedisManager redisManager;

    public static Settings settings() {
        return instance.settings;
    }

    @SuppressWarnings("unused")
    public static RedisEconomyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.settings = new Settings(this);

        //Auto server-id
        getServerId().thenAccept(s ->
                settings.SERVER_ID = s
        );

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
            new PlaceholderAPIHook(currenciesManager, instance.settings).register();
        }

        getServer().getPluginManager().registerEvents(currenciesManager, this);
        //Commands
        PayCommand payCommand = new PayCommand(currenciesManager);
        loadCommand("pay", payCommand,payCommand);
        BalanceCommand balanceCommand = new BalanceSubCommands(currenciesManager, this);
        loadCommand("balance", balanceCommand, balanceCommand);
        Objects.requireNonNull(getServer().getPluginCommand("balancetop")).setExecutor(new BalanceTopCommand(currenciesManager));
        TransactionCommand transactionCommand = new TransactionCommand(currenciesManager);
        loadCommand("transaction", transactionCommand, transactionCommand);
        PurgeUserCommand purgeUserCommand = new PurgeUserCommand(currenciesManager);
        loadCommand("purge-balance", purgeUserCommand, purgeUserCommand);
        SwitchCurrencyCommand switchCurrencyCommand = new SwitchCurrencyCommand(currenciesManager);
        loadCommand("switch-currency", switchCurrencyCommand, switchCurrencyCommand);
        loadCommand("rediseconomy", (sender, command, label, args) -> {
            if (sender.hasPermission("rediseconomy.admin")) {
                if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    String serverId = settings.SERVER_ID;
                    settings = new Settings(this);
                    settings.SERVER_ID = serverId;
                    sender.sendMessage("§aReloaded successfully " + serverId + "!");
                }
            }
            return true;
        }, (sender, command, alias, args) -> {
            if(args.length == 1) {
                return List.of("reload");
            }
            return null;
        });

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


    private CompletableFuture<String> getServerId() {
        CompletableFuture<String> future = new CompletableFuture<>();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", (channel, player, message) -> {
            if (future.isDone()) return;
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            if (subchannel.equals("GetServer")) {
                future.complete(in.readUTF());//Receive server name
            }
        });
        Listener listener = new Listener() {
            @EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                if (future.isDone()) {
                    return;
                }
                Bukkit.getScheduler().runTaskLater(instance, () -> {
                    sendServerIdRequest(event.getPlayer());
                }, 20L);

            }
        };
        if (getServer().getOnlinePlayers().size() > 0) {
            sendServerIdRequest(getServer().getOnlinePlayers().iterator().next());
        } else {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        return future.thenApply(s -> {
            //Remove listener and channel listeners
            HandlerList.unregisterAll(listener);
            getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord");
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
            return s;
        });
    }
    private void sendServerIdRequest(Player p) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());//Request server name
    }

    private boolean setupRedis() {
        String redisURI = getConfig().getString("redis-uri", "redis://localhost:6379");
        this.redisManager = new RedisManager(RedisClient.create(redisURI), getConfig().getInt("redis-connection-timeout", 3000));
        getLogger().info("Connecting to redis server " + redisURI);
        return redisManager.isConnected();
    }

    private boolean setupEconomy() {
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null)
            return false;
        this.currenciesManager = new CurrenciesManager(redisManager, this);
        currenciesManager.loadDefaultCurrency(vault);
        return true;
    }
    private void loadCommand(String cmdName, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand cmd = getServer().getPluginCommand(cmdName);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(tabCompleter);
        }else{
            getLogger().warning("Command " + cmdName + " not found!");
        }
    }

}
