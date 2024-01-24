package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
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
                    afterDate = formatDate(afterDateString);
                    beforeDate = formatDate(beforeDateString);
                }
            } catch (ParseException e) {
                plugin.langs().send(sender, plugin.langs().incorrectDate);
            }
            //If the page is not specified, we set it to 1
            int page = args.length == 4 ? Integer.parseInt(args[3]) : 1;
            //If the page is less than 1, we set it to the last page and vice versa
            if (page < 1) page = transactions.size() / 4;
            else if (page > transactions.size() / 4) page = 1;

            plugin.langs().send(sender, plugin.langs().transactionsStart
                    .replace("%player%", target)
                    .replace("%after%", afterDateString)
                    .replace("%before%", beforeDateString)
                    .replace("%page%", page + "")
                    .replace("%total_pages%", (transactions.size() / 4) + ""));

            for (int i = (page - 1) * 4; i < page * 4; i++) {
                final Transaction transaction = transactions.get(i);
                if (transaction == null) continue;

                Date transactionDate = new Date(transaction.getTimestamp());
                if (afterDate != null)
                    if (!transactionDate.after(afterDate)) continue;
                if (beforeDate != null)
                    if (!transactionDate.before(beforeDate)) continue;

                sendTransaction(sender, i, transaction, afterDateString + " " + beforeDateString);

                if (plugin.settings().debug)
                    sender.sendMessage("Time: " + (System.currentTimeMillis() - init));
            }

            plugin.langs().send(sender, plugin.langs().transactionsEnd
                    .replace("%player%", target)
                    .replace("%time%", String.valueOf(System.currentTimeMillis() - init))
                    .replace("%page%", String.valueOf(page))
                    .replace("%total_pages%", String.valueOf(transactions.size() / 4))
                    .replace("%after%", afterDateString)
                    .replace("%before%", beforeDateString)
                    .replace("%next_page%", String.valueOf(page + 1))
                    .replace("%prev_page%", String.valueOf(page - 1))
            );
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
