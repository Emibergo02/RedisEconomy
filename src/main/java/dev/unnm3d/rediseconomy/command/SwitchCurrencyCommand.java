package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@AllArgsConstructor
public class SwitchCurrencyCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INVALID_CURRENCY);
            return true;
        }
        Currency currency = currenciesManager.getCurrencyByName(args[0]);
        if (currency == null) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INVALID_CURRENCY);
            return true;
        }
        Currency newCurrency = currenciesManager.getCurrencyByName(args[1]);
        if (newCurrency == null) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INVALID_CURRENCY);
            return true;
        }
        currenciesManager.switchCurrency(currency, newCurrency);
        RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().SWITCH_SUCCESS.replace("%currency%", currency.getCurrencyName()).replace("%switch-currency%", newCurrency.getCurrencyName()));
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if(args.length > 0&&args.length < 3) {
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).toList();
        }
        return null;
    }
}
