package dev.unnm3d.rediseconomy.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class RedisManager {

    private final RedisClient lettuceRedisClient;
    private final int forcedTimeout;

    public <R> R getConnection(RedisCallBack<R> redisCallBack) {
        StatefulRedisConnection<String, String> connection = lettuceRedisClient.connect();
        try {
            return redisCallBack.useConnection(connection);
        } finally {
            CompletableFuture.delayedExecutor(forcedTimeout, java.util.concurrent.TimeUnit.MILLISECONDS).execute(connection::closeAsync);
        }
    }

    //Get pubsub
    public void getPubSubConnection(RedisCallBack.PubSub redisCallBack) {
        redisCallBack.useConnection(lettuceRedisClient.connectPubSub());
    }
    public StatefulRedisConnection<String, String> getUnclosedConnection() {
        return lettuceRedisClient.connect();
    }

    public boolean isConnected() {
        return getConnection(connection -> connection.sync().ping().equals("PONG"));
    }

    public void close() {
        lettuceRedisClient.shutdown();
    }

}
