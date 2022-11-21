package dev.unnm3d.rediseconomy.redis;

public enum RedisKeys {

    NAME_UUID("rediseco:nameuuid"),
    BALANCE_PREFIX("rediseco:balances_"),
    UPDATE_CHANNEL_PREFIX("rediseco:update_"),
    MSG_CHANNEL("rediseco:paymsg"),
    TRANSACTIONS("rediseco:transactions"),
    ;

    private final String keyName;

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

}
