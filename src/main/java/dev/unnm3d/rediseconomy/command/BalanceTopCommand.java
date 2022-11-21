package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@AllArgsConstructor
public class BalanceTopCommand implements CommandExecutor {
    private final CurrenciesManager currenciesManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        //Baltop paging, 10 per page
        currenciesManager.getDefaultCurrency().getOrderedAccounts().thenApply(balances -> {
            int page = 1;
            List<ScoredValue<String>> pageBalances;
            try {
                page = Integer.parseInt(args[0]);
            } catch (Exception ignored) {
            }
            if (balances.size() < (page - 1) * 10) {//If the page is higher that the balances available
                return new ArrayList<ScoredValue<String>>();
            } else if (balances.size() > page * 10) {
                pageBalances = balances.subList((page - 1) * 10, page * 10);
            } else pageBalances = balances.subList((page - 1) * 10, balances.size());
            //Page formatting: clickable arrows to go to next/previous page
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP.replace("%page%", String.valueOf(page))
                    .replace("%nextpage%", "<click:run_command:/balancetop " + (balances.size() <= page * 10 ? 1 : page + 1) + ">-></click>")
                    .replace("%prevpage%", "<click:run_command:/balancetop " + (page == 1 ? (balances.size() % 10) + 1 : page - 1) + "><-</click>"));

            return new PageData(page, pageBalances);
        }).thenAccept((data) -> {
            PageData pageData = (PageData) data;
            int i = 1;
            for (ScoredValue<String> tuple : pageData.pageBalances) {
                String username = currenciesManager.getUsernameFromUUIDCache(UUID.fromString(tuple.getValue()));
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP_FORMAT
                        .replace("%pos%", String.valueOf((pageData.pageNumber - 1) * 10 + i))
                        .replace("%player%", username == null ? "Unknown" : username)
                        .replace("%balance%", currenciesManager.getDefaultCurrency().format(tuple.getScore())));
                i++;
            }
        });
        return true;
    }

    private record PageData(int pageNumber, List<ScoredValue<String>> pageBalances) {
    }
}
