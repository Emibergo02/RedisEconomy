package dev.unnm3d.rediseconomy.config;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ConfigManager {
    private final RedisEconomyPlugin plugin;
    @Getter
    private Settings settings;
    @Getter
    private Langs langs;

    public ConfigManager(RedisEconomyPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
        getServerId().thenAccept(s -> {
            settings.serverId = s;
            saveConfigs();
        });
    }

    public void saveLangs() {
        plugin.saveResource("it-IT.yml", false);
    }

    public void loadConfigs() {
        YamlConfigurationProperties properties = YamlConfigurationProperties.newBuilder()
                .header(
                        """
                                ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                                ┃      RedisEconomy Config     ┃
                                ┃      Developed by Unnm3d     ┃
                                ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                        """
                )
                .footer("Authors: Unnm3d")
                .build();
        File settingsFile = new File(plugin.getDataFolder(), "config.yml");

        if (!settingsFile.exists()) saveLangs();//if config.yml doesn't exist, probably langs doesn't exist too

        settings = YamlConfigurations.update(
                settingsFile.toPath(),
                Settings.class,
                properties
        );
        Path langFile = new File(plugin.getDataFolder(), settings.lang + ".yml").toPath();
        langs = YamlConfigurations.update(
                langFile,
                Langs.class
        );
    }

    public void saveConfigs() {
        YamlConfigurations.save(new File(plugin.getDataFolder(), "config.yml").toPath(), Settings.class, settings);
    }

    private CompletableFuture<String> getServerId() {
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
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
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

    private void sendServerIdRequest(Player p) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());//Request server name
    }

}
