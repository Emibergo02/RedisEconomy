package dev.unnm3d.rediseconomy.utils;

import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final CurrenciesManager currenciesManager;
    private final Langs langs;
    private final HashMap<Currency, Double> totalSupplyCache;
    private long lastUpdateTimestamp;

    public PlaceholderAPIHook(CurrenciesManager currenciesManager, Langs langs) {
        this.currenciesManager = currenciesManager;
        this.langs = langs;
        this.totalSupplyCache = new HashMap<>();
        updateTotalSupplyCache();
    }

    private void updateTotalSupplyCache() {
        for (Currency currency : currenciesManager.getCurrencies()) {
            double totalSupply = 0;
            for (Map.Entry<UUID, Double> uuidDoubleEntry : currency.getAccounts().entrySet()) {
                totalSupply += uuidDoubleEntry.getValue();
            }
            totalSupplyCache.put(currency, totalSupply);
        }
        lastUpdateTimestamp = System.currentTimeMillis();
    }

    @Override
    public @NotNull String getAuthor() {
        return "Unnm3d";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rediseco";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    // %rediseco_balance_<currency>%
    // %rediseco_balance_formatted_<currency>%
    // %rediseco_balance_formatted_shorthand_<currency>%
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        List<String> splitted = List.of(params.split("_"));
        if (splitted.size() < 2) return null;
        Currency currency = currenciesManager.getCurrencyByName(splitted.get(splitted.size() - 1));
        if (currency == null) return "Invalid currency";

        if (splitted.get(0).equals("bal")) {
            double balance = currency.getBalance(player);
            return parseParams(balance, splitted, currency);

        } else if (splitted.get(0).equals("totsupply")) {
            if (!totalSupplyCache.containsKey(currency))
                return "Invalid currency";
            if (lastUpdateTimestamp < System.currentTimeMillis() - 1000 * 60) {
                updateTotalSupplyCache();
            }
            return parseParams(totalSupplyCache.get(currency), splitted, currency);
        }
        return null;
    }

    private String parseParams(double amount, List<String> splitted, Currency currency) {

        String formattedNumber = String.format("%.2f", amount);

        if (splitted.contains("short")) {
            if (amount >= 1000000000000.0) {
                formattedNumber = String.format("%.2f", amount / 1000000000000.0) + langs.unitSymbols.trillion();
            } else if (amount >= 1000000000.0) {
                formattedNumber = String.format("%.2f", amount / 1000000000.0) + langs.unitSymbols.billion();
            } else if (amount >= 1000000.0) {
                formattedNumber = String.format("%.2f", amount / 1000000.0) + langs.unitSymbols.million();
            } else if (amount >= 1000.0) {
                formattedNumber = String.format("%.2f", amount / 1000.0) + langs.unitSymbols.thousand();
            }
        }
        if (splitted.contains("formatted")) {
            if (amount == 1)
                formattedNumber += currency.getCurrencySingular();
            else
                formattedNumber += currency.getCurrencyPlural();
        }
        return formattedNumber;
    }
}