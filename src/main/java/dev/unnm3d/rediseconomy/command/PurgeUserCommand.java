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
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
public class PurgeUserCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        String target = args[0];
        boolean onlyNameUUID = false;
        Currency currencyReset = null;
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("onlyNameUUID")) {
                onlyNameUUID = true;
            } else if (currenciesManager.getCurrencyByName(args[1]) != null) {
                currencyReset = currenciesManager.getCurrencyByName(args[1]);
            }
        }
        Map<String, UUID> nameUUIDs;
        String successMsg;
        if (currencyReset != null) {
            nameUUIDs = currenciesManager.resetBalanceNamePattern(target, currencyReset);
            successMsg = plugin.langs().purgeBalanceSuccess.replace("%player%", target).replace("%currency%", currencyReset.getName());
        } else {
            nameUUIDs = currenciesManager.removeNamePattern(target, !onlyNameUUID);
            successMsg = plugin.langs().purgeUserSuccess.replace("%player%", target);
        }
        if (nameUUIDs.isEmpty()) {
            plugin.langs().send(sender, plugin.langs().playerNotFound);
            return true;
        }
        plugin.langs().send(sender, successMsg);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            return List.of("onlyNameUUID");
        }
        return List.of();
    }
}
