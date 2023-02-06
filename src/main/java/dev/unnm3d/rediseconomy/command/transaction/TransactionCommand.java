package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class TransactionCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        String target = args[0];
        try {
            int transactionId = Integer.parseInt(args[1]);

            boolean revertTransaction = args.length > 2 && args[2].equalsIgnoreCase("revert");

            UUID targetUUID = currenciesManager.getUUIDFromUsernameCache(target);
            AccountID accountID = targetUUID != null ? new AccountID(targetUUID) : new AccountID(target);
            if (revertTransaction) {
                if (plugin.settings().debug)
                    Bukkit.getLogger().info("revert00 Reverting transaction " + transactionId + " called by " + sender.getName());
                currenciesManager.getExchange().revertTransaction(accountID, transactionId)
                        .thenAccept(newId -> {
                            sender.sendMessage("§3Transaction reverted with #" + newId);
                            if (newId == -1)
                                sender.sendMessage("§3Transaction reverted with §cFAIL");
                        });
                return true;
            }
            currenciesManager.getExchange().getTransaction(accountID, transactionId)
                    .thenAccept(transaction -> {
                        if (transaction == null) {
                            plugin.langs().send(sender, plugin.langs().noTransactionFound.replace("%player%", target));

                        } else {
                            currenciesManager.getExchange().sendTransaction(sender, transactionId, transaction);
                        }
                    });
        } catch (NumberFormatException e) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return List.of("numeric_id");
        } else if (args.length == 3) {
            return List.of("revert");
        }
        return List.of();
    }


}
