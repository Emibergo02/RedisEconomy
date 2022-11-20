package dev.unnm3d.rediseconomy.redis;

public enum Entries {

        BALANCES("balances"),
        ;

        private final String keyName;

        /**
         * @param keyName the name of the key
         */
        Entries(final String keyName) {
            this.keyName = keyName;
        }

        @Override
        public String toString() {
            return keyName;
        }

}
