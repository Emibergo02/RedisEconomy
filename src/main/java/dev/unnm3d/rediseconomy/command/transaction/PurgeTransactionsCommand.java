package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

public class PurgeTransactionsCommand extends TransactionCommandAbstract implements CommandExecutor, TabCompleter {
    public PurgeTransactionsCommand(RedisEconomyPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        String target = args[0];
        UUID targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(target);
        AccountID accountID = targetUUID != null ? new AccountID(targetUUID) : new AccountID(target);

        try {
            plugin.getCurrenciesManager().getExchange().removeOutdatedTransactions(accountID, formatDate(args[1]).toInstant().toEpochMilli())
                    .thenAccept(removedSize ->
                            plugin.langs().send(sender, plugin.langs().purgeTransactionsSuccess
                                    .replace("%player%", target)
                                    .replace("%size%", String.valueOf(removedSize))
                                    .replace("%date%", args[1])));
        } catch (ParseException e) {
            plugin.langs().send(sender, plugin.langs().incorrectDate);
            return true;
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            return plugin.getCurrenciesManager().getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return List.of(convertTimeWithLocalTimeZome(System.currentTimeMillis()));
        }
        return List.of();
    }
}
