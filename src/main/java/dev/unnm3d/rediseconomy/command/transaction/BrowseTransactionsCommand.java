package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class BrowseTransactionsCommand extends TransactionCommandAbstract implements CommandExecutor, TabCompleter {

    public BrowseTransactionsCommand(RedisEconomyPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        final String target = args[0];
        final UUID targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(target);
        final AccountID accountID = targetUUID != null ? new AccountID(targetUUID) : new AccountID(target);
        final String afterDateString = args.length == 3 ? args[1] : "anytime";
        final String beforeDateString = args.length == 3 ? args[2] : "anytime";

        plugin.getCurrenciesManager().getExchange().getTransactions(accountID).thenAccept(transactions -> {
            long init = System.currentTimeMillis();
            if (transactions.isEmpty()) {
                plugin.langs().send(sender, plugin.langs().noTransactionFound.replace("%player%", target));
                return;
            }

            Date afterDate = null;
            Date beforeDate = null;
            try {
                if (args.length == 3) {
                    afterDate = formatDate(args[1]);
                    beforeDate = formatDate(args[2]);
                }
            } catch (ParseException e) {
                plugin.langs().send(sender, plugin.langs().incorrectDate);
            }

            plugin.langs().send(sender, plugin.langs().transactionsStart
                    .replace("%player%", target)
                    .replace("%after%", afterDateString)
                    .replace("%before%", beforeDateString));
            for (int i = 0; i < transactions.size(); i++) {
                Date transactionDate = new Date(transactions.get(i).getTimestamp());
                if (afterDate != null)
                    if (!transactionDate.after(afterDate)) continue;
                if (beforeDate != null)
                    if (!transactionDate.before(beforeDate)) continue;

                sendTransaction(sender, i, transactions.get(i), afterDateString + " " + beforeDateString);

            }

            plugin.langs().send(sender, plugin.langs().transactionsEnd
                    .replace("%player%", target)
                    .replace("%time%", String.valueOf(System.currentTimeMillis() - init)));
        });

        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            return plugin.getCurrenciesManager().getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            if (args[1].trim().isEmpty())
                return List.of("^ usage ^", convertTimeWithLocalTimeZome(System.currentTimeMillis() - 86400000) + " " + convertTimeWithLocalTimeZome(System.currentTimeMillis()), "<after the date...> <before the date...>");
        }
        return List.of();
    }


}
