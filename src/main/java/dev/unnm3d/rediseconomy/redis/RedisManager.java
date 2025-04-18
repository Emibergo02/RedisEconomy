package dev.unnm3d.rediseconomy.redis;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

public class RedisManager {

    protected static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final RoundRobinConnectionPool<String, String> roundRobinConnectionPool;
    private final List<StatefulRedisPubSubConnection<String, String>> pubSubConnections;
    protected RedisClient lettuceRedisClient;

    public RedisManager(RedisClient lettuceRedisClient, int poolSize) {
        this.lettuceRedisClient = lettuceRedisClient;
        this.roundRobinConnectionPool = new RoundRobinConnectionPool<>(lettuceRedisClient::connect, poolSize);
        pubSubConnections = new CopyOnWriteArrayList<>();
    }

    public <T> CompletionStage<T> getConnectionAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().async());
    }

    public <T> T getConnectionSync(Function<RedisCommands<String, String>, T> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().sync());
    }

    public <T> CompletionStage<T> getConnectionPipeline(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        StatefulRedisConnection<String, String> connection = roundRobinConnectionPool.get();
        connection.setAutoFlushCommands(false);
        CompletionStage<T> completionStage = redisCallBack.apply(connection.async());
        connection.flushCommands();
        connection.setAutoFlushCommands(true);
        return completionStage;
    }

    /**
     * Executes a transaction with the given Redis commands consumer.
     * If there is an exception during the transaction, it will be discarded.
     *
     * @param redisCommandsConsumer the consumer that will execute the Redis commands
     * @return an Optional containing the result of the redis transaction, or an empty Optional if the transaction was discarded
     */
    public Optional<List<Object>> executeTransaction(Consumer<RedisCommands<String, String>> redisCommandsConsumer) {
        final RedisCommands<String, String> syncCommands = roundRobinConnectionPool.get().sync();
        try {
            syncCommands.multi();
            redisCommandsConsumer.accept(syncCommands);
            return Optional.of(syncCommands.exec())
                    .filter(result -> !result.wasDiscarded())
                    .map(result -> result.stream().toList());
        } catch (Exception e) {
            syncCommands.discard();
            throw e;
        }
    }

    public StatefulRedisPubSubConnection<String, String> getPubSubConnection() {
        StatefulRedisPubSubConnection<String, String> pubSubConnection = lettuceRedisClient.connectPubSub();
        pubSubConnections.add(pubSubConnection);
        return pubSubConnection;
    }

    public void expandPool(int expandBy) {
        roundRobinConnectionPool.expandPool(expandBy);
    }

    public void printPool() {
        RedisEconomyPlugin.getInstance().getLogger().warning(roundRobinConnectionPool.printPool());
    }

    public void close() {
        pubSubConnections.forEach(StatefulRedisPubSubConnection::close);
        lettuceRedisClient.shutdown(Duration.ofSeconds(1), Duration.ofSeconds(1));
        executorService.shutdown();
    }

    public RedisFuture<String> isConnected() {
        return roundRobinConnectionPool.get().async().get("test");
    }

}
