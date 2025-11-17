package dev.unnm3d.rediseconomy.command.balance;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.utils.DecimalUtils;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;


@AllArgsConstructor
public class BalanceTopCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Currency baltopCurrency = args.length == 2 ? currenciesManager.getCurrencyByName(args[1]) : currenciesManager.getDefaultCurrency();

        if (baltopCurrency == null) {
            plugin.langs().send(sender, plugin.langs().invalidCurrency);
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            if (args[0].equals("toggle") && sender.hasPermission("rediseconomy.balancetop.toggle") &&
                    !plugin.getConfigManager().getSettings().enableHidePermissions) {
                toggleBaltop(sender, baltopCurrency);
                return true;
            }
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                plugin.langs().send(sender, plugin.langs().missingArguments);
                return true;
            }
        }

        final int finalPage = page;
        //Baltop paging, 10 per page

        baltopCurrency.getOrderedAccounts(200)
                .thenAccept(balances -> {
                    final int pageSize = 10;
                    final int start = (finalPage - 1) * pageSize;
                    final int end = finalPage * pageSize;

                    final LinkedHashMap<Double, UUID> pageBalances = new LinkedHashMap<>(pageSize);
                    int visibleCount = 0;

                    // single-pass: count visible entries and collect only the requested page
                    for (ScoredValue<String> entry : balances) {
                        final UUID playerUUID = UUID.fromString(entry.getValue());
                        if (baltopCurrency.isPlayerBaltopHidden(playerUUID)) continue;

                        if (visibleCount >= start && visibleCount < end) {
                            pageBalances.put(entry.getScore(), playerUUID);
                        }

                        visibleCount++;
                    }

                    // Page formatting: clickable arrows to go to next/previous page (use visibleCount)
                    plugin.langs().send(sender, plugin.langs().balanceTop.replace("%page%", String.valueOf(finalPage))
                            .replace("%nextpage%", "<click:run_command:balancetop " + (visibleCount <= finalPage * pageSize ? 1 : finalPage + 1) + ">-></click>")
                            .replace("%prevpage%", "<click:run_command:balancetop " + (finalPage == 1 ? (visibleCount / pageSize) + 1 : finalPage - 1) + "><-</click>"));

                    int i = 1;
                    for (Map.Entry<Double, UUID> doubleUUIDEntry : pageBalances.entrySet()) {
                        final String username = currenciesManager.getUsernameFromUUIDCache(doubleUUIDEntry.getValue());
                        plugin.langs().send(sender, plugin.langs().balanceTopFormat
                                .replace("%pos%", String.valueOf((finalPage - 1) * 10 + i))
                                .replace("%player%", username == null ? doubleUUIDEntry.getValue() + "-Unknown" : username)
                                .replace("%balance_short%",
                                        DecimalUtils.shortAmount(doubleUUIDEntry.getKey(), baltopCurrency.getDecimalFormat()) +
                                                (doubleUUIDEntry.getKey() == 1 ? baltopCurrency.getCurrencySingular() : baltopCurrency.getCurrencyPlural()))
                                .replace("%balance%", baltopCurrency.format(doubleUUIDEntry.getKey())));
                        i++;
                    }


                });
        return true;
    }

    private void toggleBaltop(CommandSender sender, Currency baltopCurrency) {
        if (!(sender instanceof Player player)) {
            plugin.langs().send(sender, plugin.langs().noConsole);
            return;
        }
        boolean newState = !baltopCurrency.isPlayerBaltopHidden(player.getUniqueId());
        baltopCurrency.hidePlayerBaltop(player.getUniqueId(), newState);
        if (newState) {
            plugin.langs().send(sender, plugin.langs().baltopHidden);
        } else {
            plugin.langs().send(sender, plugin.langs().baltopShown);
        }

    }

    private record PageData(int pageNumber, List<ScoredValue<String>> pageBalances) {
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            final ArrayList<String> results = new ArrayList<>(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
            if (sender.hasPermission("rediseconomy.balancetop.toggle") &&
                    !plugin.getConfigManager().getSettings().enableHidePermissions) results.add("toggle");
            return results;
        } else if (args.length == 2) {
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).toList();
        }
        return List.of();
    }
}
