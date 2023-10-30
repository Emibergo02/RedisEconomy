package dev.unnm3d.rediseconomy.command.balance;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@AllArgsConstructor
public class BalanceTopCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        int page = 1;
        if (args.length == 1)
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                plugin.langs().send(sender, plugin.langs().missingArguments);
                return true;
            }

        Currency baltopCurrency = args.length == 2 ? currenciesManager.getCurrencyByName(args[1]) : currenciesManager.getDefaultCurrency();

        if (baltopCurrency == null) {
            plugin.langs().send(sender, plugin.langs().invalidCurrency);
            return true;
        }

        final int finalPage = page;
        //Baltop paging, 10 per page
        baltopCurrency.getOrderedAccounts(200)
                .thenApply(balances -> {
                    List<ScoredValue<String>> pageBalances;
                    if (balances.size() < (finalPage - 1) * 10) {//If the page is higher that the balances available
                        return new ArrayList<ScoredValue<String>>();
                    } else if (balances.size() > finalPage * 10) {
                        pageBalances = balances.subList((finalPage - 1) * 10, finalPage * 10);
                    } else pageBalances = balances.subList((finalPage - 1) * 10, balances.size());
                    //Page formatting: clickable arrows to go to next/previous page
                    plugin.langs().send(sender, plugin.langs().balanceTop.replace("%page%", String.valueOf(finalPage))
                            .replace("%nextpage%", "<click:run_command:/balancetop " + (balances.size() <= finalPage * 10 ? 1 : finalPage + 1) + ">-></click>")
                            .replace("%prevpage%", "<click:run_command:/balancetop " + (finalPage == 1 ? (balances.size() % 10) + 1 : finalPage - 1) + "><-</click>"));

                    return new PageData(finalPage, pageBalances);
                })
                .thenAccept((data) -> {
                    PageData pageData = (PageData) data;
                    int i = 1;
                    for (ScoredValue<String> tuple : pageData.pageBalances) {
                        String username = currenciesManager.getUsernameFromUUIDCache(UUID.fromString(tuple.getValue()));
                        plugin.langs().send(sender, plugin.langs().balanceTopFormat
                                .replace("%pos%", String.valueOf((pageData.pageNumber - 1) * 10 + i))
                                .replace("%player%", username == null ? tuple.getValue() + "-Unknown" : username)
                                .replace("%balance%", baltopCurrency.format(tuple.getScore())));
                        i++;
                    }
                });
        return true;
    }

    private record PageData(int pageNumber, List<ScoredValue<String>> pageBalances) {
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        } else if (args.length == 2) {
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).toList();
        }
        return List.of();
    }
}
