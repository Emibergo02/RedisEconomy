package dev.unnm3d.rediseconomy.api;

import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public abstract class RedisEconomyAPI {
    protected static RedisEconomyAPI INSTANCE = null;

    /**
     * Get API instance
     *
     * @return the API instance
     */
    public static @Nullable RedisEconomyAPI getAPI() {
        return INSTANCE;
    }

    /**
     * Get transaction manager
     *
     * @return the EconomyExchange instance
     */
    public abstract @NotNull EconomyExchange getExchange();

    /**
     * Get all the currencies
     *
     * @return all the currencies in a collection
     */
    public abstract @NotNull Collection<Currency> getCurrencies();

    /**
     * Get all the currencies
     *
     * @return all the currencies
     */
    public abstract @NotNull Map<String, Currency> getCurrenciesWithNames();

    /**
     * Get a currency by its name
     *
     * @param name the name of the currency
     * @return the currency
     */
    public abstract @Nullable Currency getCurrencyByName(@NotNull String name);

    /**
     * Get default currency (vault currency)
     *
     * @return the default currency
     */
    public abstract @NotNull Currency getDefaultCurrency();

    /**
     * Get a playerName by its case-insensitive name
     *
     * @param caseInsensitiveName the case-insensitive name of the player
     * @return the case-sensitive name of the player
     */
    public abstract @NotNull String getCaseSensitiveName(@NotNull String caseInsensitiveName);

    /**
     * Get a currency by its symbol
     *
     * @param symbol the symbol of the currency
     * @return the currency
     */
    public abstract @Nullable Currency getCurrencyBySymbol(@NotNull String symbol);

    /**
     * Get uuid from cache
     *
     * @param username the username of the player
     * @return the uuid of the player
     */
    public abstract @Nullable UUID getUUIDFromUsernameCache(@NotNull String username);

    /**
     * Get username from cache
     *
     * @param uuid the uuid of the player
     * @return the username of the player
     */
    public abstract @Nullable String getUsernameFromUUIDCache(@NotNull UUID uuid);

}
