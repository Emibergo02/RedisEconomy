package dev.unnm3d.rediseconomy.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.AllArgsConstructor;

import java.time.Duration;

@AllArgsConstructor
public class RedisManager {

    private RedisClient lettuceRedisClient;
    private int forcedTimeout;

    public <R> R getConnection(RedisCallBack<R> redisCallBack) {
        StatefulRedisConnection<String, String> connection = lettuceRedisClient.connect();
        connection.setTimeout(Duration.ofMillis(forcedTimeout));
        return redisCallBack.useConnection(connection);
    }

    //Get pubsub
    public void getPubSubConnection(RedisCallBack.PubSub redisCallBack) {
        redisCallBack.useConnection(lettuceRedisClient.connectPubSub());
    }

    public boolean isConnected() {
        return getConnection(connection -> connection.sync().ping().equals("PONG"));
    }

    public void close() {
        lettuceRedisClient.shutdown();
    }

}
