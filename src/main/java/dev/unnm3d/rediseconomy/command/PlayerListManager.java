package dev.unnm3d.rediseconomy.command;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.RedisEconomyListener;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.redis.RedisManager;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.entity.HumanEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListManager {
    private final MyScheduledTask task;
    private final ConcurrentHashMap<String, Long> playerList;
    private final StatefulRedisPubSubConnection<String, String> connection;

    public PlayerListManager(RedisManager redisManager, RedisEconomyPlugin plugin) {
        this.playerList = new ConcurrentHashMap<>();
        this.connection = redisManager.getPubSubConnection();
        this.connection.addListener(new RedisEconomyListener() {
            @Override
            public void message(String channel, String message) {
                long currentTimeMillis = System.currentTimeMillis();
                for (String playerName : message.split(",")) {
                    if (!playerName.isEmpty())
                        playerList.put(playerName, currentTimeMillis);
                }
            }
        });
        this.connection.async().subscribe(RedisKeys.UPDATE_ONLINE.toString());
        this.task = new UniversalRunnable() {
            @Override
            public void run() {
                playerList.entrySet().removeIf(stringLongEntry -> System.currentTimeMillis() - stringLongEntry.getValue() > 1000 * 4);

                final List<String> tempList = plugin.getServer().getOnlinePlayers().stream()
                        .map(HumanEntity::getName)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!tempList.isEmpty())
                    redisManager.getConnectionSync(connection ->
                            connection.publish(RedisKeys.UPDATE_ONLINE.toString(), String.join(",", tempList)));
                tempList.forEach(s -> playerList.put(s, System.currentTimeMillis()));
            }
        }.runTaskTimerAsynchronously(plugin, 0, 60);//3 seconds
    }

    public Set<String> getOnlinePlayers() {
        return playerList.keySet();
    }

    public void stop() {
        this.connection.close();
        task.cancel();
    }

}
