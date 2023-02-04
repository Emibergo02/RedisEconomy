package dev.unnm3d.rediseconomy.redis;

import dev.unnm3d.rediseconomy.redis.redistools.RoundRobinConnectionPool;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;


public abstract class RedisAbstract {
    protected static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final RoundRobinConnectionPool<String, String> roundRobinConnectionPool;
    protected RedisClient lettuceRedisClient;

    public RedisAbstract(RedisClient lettuceRedisClient) {
        this.lettuceRedisClient = lettuceRedisClient;
        this.roundRobinConnectionPool = new RoundRobinConnectionPool<>(lettuceRedisClient::connect, 5);
    }

    public <T> ScheduledFuture<T> scheduleConnection(Function<StatefulRedisConnection<String, String>, T> function, int timeout, TimeUnit timeUnit) {
        return executorService.schedule(() -> function.apply(roundRobinConnectionPool.get()), timeout, timeUnit);
    }

    public <T> CompletionStage<T> getConnectionAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().async());
    }

    public void close() {
        lettuceRedisClient.shutdown(Duration.ofSeconds(1), Duration.ofSeconds(1));
        roundRobinConnectionPool.close();
        executorService.shutdown();
    }

}
