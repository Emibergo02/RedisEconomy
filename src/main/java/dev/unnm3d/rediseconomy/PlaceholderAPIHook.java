package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final CurrenciesManager currenciesManager;

    public PlaceholderAPIHook(CurrenciesManager currenciesManager) {
        this.currenciesManager = currenciesManager;
    }

    @Override
    public String getAuthor() {
        return "Unnm3d";
    }

    @Override
    public String getIdentifier() {
        return "rediseco";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    // %rediseco_balance_<currency>%
    // %rediseco_balanceformatted_<currency>%
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if(params.startsWith("balance_")){
            String[] args = params.split("_");
            if(args.length==2){
                if(args[1].equals(""))
                    return "Invalid currency";
                Currency currency=currenciesManager.getCurrencyByName(args[1]);
                if(currency==null){
                    return "Invalid currency";
                }
                return String.format("%.2f", currency.getBalance(player));
            }
        } else if (params.startsWith("balanceformatted_")) {
            String[] args = params.split("_");
            if(args.length==2){
                if(args[1].equals(""))
                    return "Invalid currency";
                Currency currency=currenciesManager.getCurrencyByName(args[1]);
                if(currency==null){
                    return "Invalid currency";
                }
                return currency.format(currency.getBalance(player));
            }
        }

        return null;
    }
}