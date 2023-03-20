package dev.unnm3d.rediseconomy.redis;

public enum RedisKeys {

    NAME_UUID("rediseco:nameuuid"),
    BALANCE_PREFIX("rediseco:balances_"),
    BALANCE_BANK_PREFIX("rediseco:b_balances_"),
    BANK_OWNERS("rediseco:b_owners"),
    UPDATE_PLAYER_CHANNEL_PREFIX("rediseco:update_"),
    UPDATE_BANK_CHANNEL_PREFIX("rediseco:b_update_"),
    UPDATE_BANK_OWNER_CHANNEL_PREFIX("rediseco:b_owner_update_"),
    MSG_CHANNEL("rediseco:paymsg"),
    NEW_TRANSACTIONS("rediseco:transactions:"),
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

}
