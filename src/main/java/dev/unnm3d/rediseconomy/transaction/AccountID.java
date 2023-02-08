package dev.unnm3d.rediseconomy.transaction;

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
        this.id = Transaction.getServerUUID().toString();
    }

    public boolean isPlayer() {
        return id.length() == 36;
    }

    public UUID getUUID() {
        return UUID.fromString(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
