package dev.unnm3d.rediseconomy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.unnm3d.rediseconomy.command.*;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import io.lettuce.core.RedisClient;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

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

        if (!setupEconomy()) { //creates currenciesManager
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            this.getServer().getPluginManager().disablePlugin(this);

        } else {
            this.getLogger().info("Hooked into Vault!");
        }

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
        String uri=getConfig().getString("redis-url", "redis://localhost:6379");
        int timeout=getConfig().getInt("redis-connection-timeout", 3000);
        this.redisManager = new RedisManager(RedisClient.create(uri), timeout);
        getLogger().info("Connecting to redis server "+uri+" with timeout "+timeout+"ms");
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


}
