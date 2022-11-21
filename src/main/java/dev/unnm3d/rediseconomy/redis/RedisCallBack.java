package dev.unnm3d.rediseconomy.redis;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface RedisCallBack<R> {
    @Nullable R useConnection(StatefulRedisConnection<String, String> connection);

    @FunctionalInterface
    interface PubSub {
        void useConnection(StatefulRedisPubSubConnection<String, String> connection);
    }
}
