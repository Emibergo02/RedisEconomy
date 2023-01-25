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

import java.util.List;

@AllArgsConstructor
public class SwitchCurrencyCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        Currency currency = currenciesManager.getCurrencyByName(args[0]);
        if (currency == null) {
            plugin.langs().send(sender, plugin.langs().invalidCurrency);
            return true;
        }
        Currency newCurrency = currenciesManager.getCurrencyByName(args[1]);
        if (newCurrency == null) {
            plugin.langs().send(sender, plugin.langs().invalidCurrency);
            return true;
        }
        currenciesManager.switchCurrency(currency, newCurrency);
        plugin.langs().send(sender, plugin.langs().switchCurrencySuccess.replace("%currency%", currency.getCurrencyName()).replace("%switch-currency%", newCurrency.getCurrencyName()));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length > 0 && args.length < 3) {
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).toList();
        }
        return List.of();
    }
}
