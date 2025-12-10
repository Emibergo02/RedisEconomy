package dev.unnm3d.rediseconomy.messaging;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.concurrent.CompletionStage;

/**
 * Interface for messaging operations.
 * This abstraction allows for future implementations of different messaging systems
 * beyond Redis pub/sub (e.g., RabbitMQ, Kafka, etc.)
 */
public interface Messaging {

    /**
     * Get a pub/sub connection for registering listeners
     *
     * @return A pub/sub connection
     */
    StatefulRedisPubSubConnection<String, String> getPubSubConnection();

    /**
     * Publish a message to a channel
     *
     * @param channel The channel to publish to
     * @param message The message to publish
     * @return CompletionStage that completes when the message is published
     */
    CompletionStage<Long> publish(String channel, String message);
}
