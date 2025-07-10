package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.redis.RedisKeys;

import java.util.UUID;

public class AccountID {
    private final String id;

    /**
     * Creates a new non-player account id
     *
     * @param id The account id. It must be less than 36 characters
     */
    public AccountID(String id) {
        if (id.length() >= 36) {
            throw new IllegalArgumentException("Invalid account id. It must be less than 36 characters");
        }
        this.id = id;
    }

    /**
     * Creates a new player account id
     *
     * @param id The account id.
     */
    public AccountID(UUID id) {
        this.id = id.toString();
    }

    public AccountID() {
        this.id = RedisKeys.getServerUUID().toString();
    }

    /**
     * Returns if the account id is a player or a bank id (non UUID)
     *
     * @return true if the account id is a player uuid
     */
    public boolean isPlayer() {
        return id.length() == 36;
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
