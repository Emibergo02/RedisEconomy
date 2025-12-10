package dev.unnm3d.rediseconomy.messaging;

import dev.unnm3d.rediseconomy.redis.RedisManager;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.concurrent.CompletionStage;

/**
 * Redis implementation of the Messaging interface.
 * Uses Redis pub/sub for message passing between instances.
 */
public class RedisMessaging implements Messaging {

    private final RedisManager redisManager;

    public RedisMessaging(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    @Override
    public StatefulRedisPubSubConnection<String, String> getPubSubConnection() {
        return redisManager.getPubSubConnection();
    }

    @Override
    public CompletionStage<Long> publish(String channel, String message) {
        return redisManager.getConnectionAsync(commands -> 
            commands.publish(channel, message));
    }
}
