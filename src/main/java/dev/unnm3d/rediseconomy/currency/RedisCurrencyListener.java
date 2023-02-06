package dev.unnm3d.rediseconomy.currency;

import io.lettuce.core.pubsub.RedisPubSubListener;

public abstract class RedisCurrencyListener implements RedisPubSubListener<String, String> {

    @Override
    public abstract void message(String channel, String message);

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
