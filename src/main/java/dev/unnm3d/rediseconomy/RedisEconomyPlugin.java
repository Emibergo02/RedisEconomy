package dev.unnm3d.rediseconomy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.unnm3d.ezredislib.EzRedisMessenger;
import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class RedisEconomyPlugin extends JavaPlugin {

    private static RedisEconomyPlugin instance;
    private EzRedisMessenger ezRedisMessenger;
    private Settings settings;
    private CurrenciesManager currenciesManager;

    public static Settings settings() {
        return instance.settings;
    }

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
        }

        if (!setupEconomy()) {
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            this.getLogger().info("Hooked into Vault!");
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(currenciesManager).register();
        }

        registerRedisChannels();

        EconomyExchange exchange = new EconomyExchange(currenciesManager);

        getServer().getPluginManager().registerEvents(currenciesManager, this);
        PayCommand payCommand = new PayCommand(currenciesManager, exchange);
        getServer().getPluginCommand("pay").setExecutor(payCommand);
        getServer().getPluginCommand("pay").setTabCompleter(payCommand);

        BalanceCommand balanceCommand = new BalanceSubCommands(currenciesManager, this);
        getServer().getPluginCommand("balance").setExecutor(balanceCommand);
        getServer().getPluginCommand("balance").setTabCompleter(balanceCommand);
        getServer().getPluginCommand("balancetop").setExecutor(new BalanceTopCommand(currenciesManager));

        TransactionCommand transactionCommand = new TransactionCommand(currenciesManager, exchange);
        getServer().getPluginCommand("transaction").setExecutor(transactionCommand);
        getServer().getPluginCommand("transaction").setTabCompleter(transactionCommand);

        new Metrics(this, 16802);
    }

    @Override
    public void onDisable() {
        ezRedisMessenger.destroy();
        this.getServer().getServicesManager().unregister(Economy.class, currenciesManager.getDefaultCurrency());
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    private void registerRedisChannels() {
        ezRedisMessenger.registerChannelObjectListener("rediseco:paymsg", (packet) -> {
            PayCommand.PayMsg payMsgPacket = (PayCommand.PayMsg) packet;
            Player online = getServer().getPlayer(payMsgPacket.receiverName());
            if (online != null) {
                if (online.isOnline()) {
                    settings.send(online, settings.PAY_RECEIVED.replace("%player%", payMsgPacket.sender()).replace("%amount%", payMsgPacket.amount()));

                    if (RedisEconomyPlugin.settings().DEBUG) {
                        Bukkit.getLogger().info("Received pay message to " + online.getName() + " timestamp: " + System.currentTimeMillis());
                    }
                }
            }
        }, PayCommand.PayMsg.class);
    }

    private CompletableFuture<String> getServerId() {
        CompletableFuture<String> future = new CompletableFuture<>();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", (channel, player, message) -> {
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
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("GetServer");
                event.getPlayer().sendPluginMessage(instance, "BungeeCord", out.toByteArray());//Request server name
            }
        };
        getServer().getPluginManager().registerEvents(listener, this);//Register listener on player join

        return future.thenApply(s -> {
            //Remove listener and channel listeners
            HandlerList.unregisterAll(listener);
            this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord");
            this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
            return s;
        });
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
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null)
            return false;
        this.currenciesManager = new CurrenciesManager(ezRedisMessenger, this);
        currenciesManager.loadDefaultCurrency(vault);
        return true;
    }


}
