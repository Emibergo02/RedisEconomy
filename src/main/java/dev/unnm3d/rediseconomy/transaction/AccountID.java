package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.redis.RedisKeys;
import lombok.Getter;

import java.util.UUID;

public class AccountID {
    private final String id;
    @Getter
    private final boolean isPlayer;

    /**
     * Creates a new non-player account id
     *
     * @param id The account id. It must be less than 36 characters
     */
    public AccountID(String id) {
        if (id.length() >= 16) {
            throw new IllegalArgumentException("Invalid account id. It must be less than 16 characters");
        }
        this.id = id;
        this.isPlayer = false;
    }

    /**
     * Creates a new player account id
     *
     * @param id The account id.
     */
    public AccountID(UUID id) {
        this.id = id.toString();
        this.isPlayer = true;
    }

    public AccountID() {
        this.id = RedisKeys.getServerUUID().toString();
        this.isPlayer = true;
    }

    public boolean isServer() {
        if (!isPlayer()) return false;
        return getUUID().equals(RedisKeys.getServerUUID());
    }

    public UUID getUUID() {
        return UUID.fromString(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
