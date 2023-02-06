package dev.unnm3d.rediseconomy.transaction;

import java.util.UUID;

public class AccountID {
    private final String id;

    public AccountID(String id) {
        if (id.length() >= 36) {
            throw new IllegalArgumentException("Invalid account id. It must be less than 36 characters");
        }
        this.id = id;
    }

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
