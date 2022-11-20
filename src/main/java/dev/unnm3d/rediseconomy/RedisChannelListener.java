package dev.unnm3d.rediseconomy;

import io.lettuce.core.pubsub.RedisPubSubListener;
import lombok.AllArgsConstructor;
import org.bukkit.Server;
import org.bukkit.entity.Player;

@AllArgsConstructor
public class RedisChannelListener implements RedisPubSubListener<String,String> {

    private RedisEconomyPlugin plugin;

    @Override
    public void message(String channel, String message) {
        System.out.println("Message received: " + message);
        String[] args = message.split(";;");
        String sender = args[0];
        String target = args[1];
        String currencyAmount = args[2];
            Player online = plugin.getServer().getPlayer(target);
            if (online != null) {
                if (online.isOnline()) {
                    plugin.getSettings().send(online, plugin.getSettings().PAY_RECEIVED.replace("%player%", sender).replace("%amount%", currencyAmount));
                    if (RedisEconomyPlugin.settings().DEBUG) {
                        plugin.getLogger().info("Received pay message to " + online.getName() + " timestamp: " + System.currentTimeMillis());
                    }
                }
            }
    }

    @Override
    public void message(String pattern, String channel, String message) {

    }

    @Override
    public void subscribed(String channel, long count) {

    }

    @Override
    public void psubscribed(String pattern, long count) {

    }

    @Override
    public void unsubscribed(String channel, long count) {

    }

    @Override
    public void punsubscribed(String pattern, long count) {

    }
}
