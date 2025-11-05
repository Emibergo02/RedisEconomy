package dev.unnm3d.rediseconomy.redis;

import java.util.UUID;

public enum RedisKeys {

    NAME_UUID("rediseco:nameuuid"),
    MAX_PLAYER_BALANCES("rediseco:max_bals"),
    BALANCE_PREFIX("rediseco:balances_"),
    BALANCE_BANK_PREFIX("rediseco:b_balances_"),
    BANK_OWNERS("rediseco:b_owners"),
    UPDATE_PLAYER_CHANNEL_PREFIX("rediseco:update_"),
    UPDATE_MAX_BAL_PREFIX("rediseco:update_max_"),
    UPDATE_BANK_CHANNEL_PREFIX("rediseco:b_update_"),
    UPDATE_BANK_OWNER_CHANNEL_PREFIX("rediseco:b_owner_update_"),
    MSG_CHANNEL("rediseco:paymsg"),
    TRANSACTIONS("rediseco:transactions:"),
    TRANSACTIONS_COUNTER("rediseco:transactions_counter"),
    LOCKED_ACCOUNTS("rediseco:locked"),
    UPDATE_LOCKED_ACCOUNTS("rediseco:locked"),
    UPDATE_ONLINE("rediseco:online_update"),
    BALTOP_HIDDEN_ACCOUNTS("rediseco:hiddenacc"),
    UPDATE_BALTOP_HIDDEN_ACCOUNTS("rediseco:hiddenaccup_"),
    BALTOP_HIDDEN_BANK_ACCOUNTS("rediseco:hiddenbacc"),
    UPDATE_BALTOP_HIDDEN_BANK_ACCOUNTS("rediseco:hiddenbaccup_"),
    ;

    private String keyName;

    /**
     * @param keyName the name of the key
     */
    RedisKeys(final String keyName) {
        this.keyName = keyName;
    }

    @Override
    public String toString() {
        return keyName;
    }

    public static void setClusterId(String clusterId) {
        for (RedisKeys key : values()) {
            key.keyName = clusterId + "-" + key.keyName;
        }
    }

    /**
     * Returns the server UUID
     *
     * @return "00000000-0000-0000-0000-000000000000" as UUID
     */
    public static UUID getServerUUID() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    /**
     * Returns a UUID that represents all accounts
     *
     * @return "ffffffff-ffff-ffff-ffff-ffffffffffff" as UUID
     */
    public static UUID getAllAccountUUID() {
        return UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    }

}
