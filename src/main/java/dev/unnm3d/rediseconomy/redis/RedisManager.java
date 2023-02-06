package dev.unnm3d.rediseconomy.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

public class RedisManager {

    protected static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final RoundRobinConnectionPool<String, String> roundRobinConnectionPool;
    private final List<StatefulRedisPubSubConnection<String, String>> pubSubConnections;
    protected RedisClient lettuceRedisClient;

    public RedisManager(RedisClient lettuceRedisClient) {
        this.lettuceRedisClient = lettuceRedisClient;
        this.roundRobinConnectionPool = new RoundRobinConnectionPool<>(lettuceRedisClient::connect, 5);
        pubSubConnections = new CopyOnWriteArrayList<>();
    }

    public <T> ScheduledFuture<T> scheduleConnection(Function<StatefulRedisConnection<String, String>, T> function, int timeout, TimeUnit timeUnit) {
        return executorService.schedule(() -> function.apply(roundRobinConnectionPool.get()), timeout, timeUnit);
    }

    public <T> CompletionStage<T> getConnectionAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().async());
    }

    public <T> CompletionStage<T> getConnectionPipeline(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        StatefulRedisConnection<String, String> connection = roundRobinConnectionPool.get();
        connection.setAutoFlushCommands(false);
        CompletionStage<T> completionStage = redisCallBack.apply(roundRobinConnectionPool.get().async());
        connection.flushCommands();
        connection.setAutoFlushCommands(true);
        return completionStage;
    }

    public StatefulRedisPubSubConnection<String, String> getPubSubConnection() {
        StatefulRedisPubSubConnection<String, String> pubSubConnection = lettuceRedisClient.connectPubSub();
        pubSubConnections.add(pubSubConnection);
        return pubSubConnection;
    }

    public void close() {
        pubSubConnections.forEach(StatefulRedisPubSubConnection::close);
        lettuceRedisClient.shutdown(Duration.ofSeconds(1), Duration.ofSeconds(1));
        roundRobinConnectionPool.close();
        executorService.shutdown();
    }

    public RedisFuture<String> isConnected() {
        return roundRobinConnectionPool.get().async().get("test");
    }

}
