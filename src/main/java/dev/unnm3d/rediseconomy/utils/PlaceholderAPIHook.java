package dev.unnm3d.rediseconomy.utils;

import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final CurrenciesManager currenciesManager;
    private final Langs langs;
    private final HashMap<Currency, Double> totalSupplyCache;
    private final HashMap<Currency, List<String>> baltopCache;
    private final int updateCachePeriod;
    private long lastUpdateTimestamp;

    public PlaceholderAPIHook(CurrenciesManager currenciesManager, Langs langs) {
        this.currenciesManager = currenciesManager;
        this.langs = langs;
        this.totalSupplyCache = new HashMap<>();
        this.baltopCache = new HashMap<>();
        this.updateCachePeriod = 1000 * 60; // 1 minute
        this.lastUpdateTimestamp = 0;
        updatePlaceholdersCache();
    }

    private void updatePlaceholdersCache() {
        if (System.currentTimeMillis() - lastUpdateTimestamp < updateCachePeriod) return;
        for (Currency currency : currenciesManager.getCurrencies()) {
            //Total Supply
            double totalSupply = 0;
            for (Map.Entry<UUID, Double> uuidDoubleEntry : currency.getAccounts().entrySet()) {
                totalSupply += uuidDoubleEntry.getValue();
            }
            totalSupplyCache.put(currency, totalSupply);

            //Balance top
            currency.getOrderedAccounts(10).thenAccept(accounts -> {
                List<String> baltopList = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    if (accounts.size() <= i) break;
                    baltopList.add(
                            currenciesManager.getUsernameFromUUIDCache(UUID.fromString(accounts.get(i).getValue())) +
                                    ";" +
                                    accounts.get(i).getScore());
                }
                baltopCache.put(currency, baltopList);
            });
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
    // %rediseco_top_1_name_shorthand_<currency>%
    // %rediseco_top_1_bal_shorthand_<currency>%
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        List<String> splitted = List.of(params.split("_"));
        if (splitted.size() < 2) return null;
        Currency currency = currenciesManager.getCurrencyByName(splitted.get(splitted.size() - 1));
        if (currency == null) return "Invalid currency";

        if (splitted.get(0).equals("bal")) {
            double balance = currency.getBalance(player);
            return parseParams(balance, splitted, currency);

        }

        updatePlaceholdersCache();

        if (splitted.get(0).equals("totsupply")) {

            return parseParams(totalSupplyCache.get(currency), splitted, currency);
        } else if (splitted.get(0).equals("top")) {

            int position = Integer.parseInt(splitted.get(1));
            if (position < 1 || position > 10) return "N/A"; //Invalid positions

            if (splitted.size() < 4) return null; //Insufficient parameters

            List<String> user_balance_strings = baltopCache.get(currency);
            if (user_balance_strings == null) return null;
            if (user_balance_strings.size() < position) return "N/A";

            if (splitted.get(2).equals("bal")) {
                double balance = Double.parseDouble(user_balance_strings.get(position - 1).split(";")[1]);
                return parseParams(balance, splitted, currency);
            } else if (splitted.get(2).equals("name")) {
                return user_balance_strings.get(position - 1).split(";")[0];
            }
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