package dev.unnm3d.rediseconomy.config;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.struct.CurrencySettings;
import dev.unnm3d.rediseconomy.config.struct.RedisSettings;
import dev.unnm3d.rediseconomy.config.struct.TransactionItem;
import dev.unnm3d.rediseconomy.config.struct.UnitSymbols;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.xezard.configurations.bukkit.serialization.ConfigurationSerialization;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class ConfigManager {
    private final RedisEconomyPlugin plugin;
    @Getter
    private Settings settings;
    @Getter
    private Langs langs;

    public ConfigManager(RedisEconomyPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSerialization.registerClass(RedisSettings.class);
        ConfigurationSerialization.registerClass(CurrencySettings.class);

        ConfigurationSerialization.registerClass(TransactionItem.class);
        ConfigurationSerialization.registerClass(UnitSymbols.class);

        loadSettingsConfig();
    }

    public void postStartupLoad() {
        loadLangs();
        getServerId().thenAccept(s -> {
            settings.serverId = s;
            saveConfigs();
        });
    }

    public void loadSettingsConfig() {

        settings = new Settings(plugin.getDataFolder());
        File settingsFile = new File(settings.getPathToFile());
        if (!settingsFile.exists()) settings.load(true);
        else settings.load();
    }

    public void saveConfigs() {
        settings.save();
        langs.save();
    }

    public void loadLangs() {
        File settingsFile = new File(plugin.getDataFolder(), settings.lang + ".yml");
        if (!settingsFile.exists()) {
            plugin.saveResource("it-IT.yml", false);//save default lang
        }
        langs = new Langs(settingsFile.getAbsolutePath());
        langs.load();
    }

    @SuppressWarnings("UnstableApiUsage")
    public CompletableFuture<String> getServerId() {
        CompletableFuture<String> future = new CompletableFuture<>();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", (channel, player, message) -> {
            if (future.isDone()) return;
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            if (subchannel.equals("GetServer")) {
                future.complete(in.readUTF());//Receive server name
            }
        });
        Listener listener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (future.isDone()) {
                    return;
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> sendServerIdRequest(event.getPlayer()), 20L);

            }
        };
        if (plugin.getServer().getOnlinePlayers().size() > 0) {
            sendServerIdRequest(plugin.getServer().getOnlinePlayers().iterator().next());
        } else {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        return future.thenApply(s -> {
            //Remove listener and channel listeners
            HandlerList.unregisterAll(listener);
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "BungeeCord");
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "BungeeCord");
            return s;
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private void sendServerIdRequest(Player p) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());//Request server name
    }

}
