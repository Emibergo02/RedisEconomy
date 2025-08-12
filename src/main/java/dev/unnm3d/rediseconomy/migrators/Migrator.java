package dev.unnm3d.rediseconomy.migrators;

import dev.unnm3d.rediseconomy.currency.Currency;

public interface Migrator {

    void migrate(Currency defaultCurrency);
}
