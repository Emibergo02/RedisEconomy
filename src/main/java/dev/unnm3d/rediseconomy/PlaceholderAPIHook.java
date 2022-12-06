package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import lombok.AllArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@AllArgsConstructor
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final CurrenciesManager currenciesManager;
    private final Settings settings;


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
        if (!splitted.get(0).equals("balance")) return null;

        Currency currency = currenciesManager.getCurrencyByName(splitted.get(splitted.size() - 1));
        if (currency == null) return "Invalid currency";

        double balance = currency.getBalance(player);
        String formattedNumber = String.format("%.2f", balance);

        if (splitted.contains("shorthand")) {
            if (balance >= 1000000000000.0) {
                formattedNumber = String.format("%.2f", balance / 1000000000000.0) + settings.UNIT_SYMBOLS.trillions();
            } else if (balance >= 1000000000.0) {
                formattedNumber = String.format("%.2f", balance / 1000000000.0) + settings.UNIT_SYMBOLS.billions();
            } else if (balance >= 1000000.0) {
                formattedNumber = String.format("%.2f", balance / 1000000.0) + settings.UNIT_SYMBOLS.millions();
            } else if (balance >= 1000.0) {
                formattedNumber = String.format("%.2f", balance / 1000.0) + settings.UNIT_SYMBOLS.thousands();
            }
        }
        if (splitted.contains("formatted")) {
            if (balance == 1)
                formattedNumber += currency.getCurrencySingular();
            else
                formattedNumber += currency.getCurrencyPlural();
        }
        return formattedNumber;
    }
}