package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;


public class TransactionCommand extends TransactionCommandAbstract implements CommandExecutor, TabCompleter {


    public TransactionCommand(RedisEconomyPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        String target = args[0];
        try {
            long transactionId = Long.parseLong(args[1]);
            UUID targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(target);
            AccountID accountID = targetUUID != null ? new AccountID(targetUUID) : new AccountID(target);

            if (!accountID.isPlayer() && target.length() > 16) {
                plugin.langs().send(sender, plugin.langs().truncatedID);
            }
            if (args.length > 2 && args[2].equalsIgnoreCase("revert")) {
                RedisEconomyPlugin.debug("revert00 Reverting transaction " + transactionId + " called by " + sender.getName());
                plugin.getCurrenciesManager().getExchange().revertTransaction(accountID, transactionId)
                        .thenAccept(newId -> {
                            sender.sendMessage("§3Transaction reverted with #" + newId);
                            if (newId == -1)
                                sender.sendMessage("§3Transaction reverted with §cFAIL");
                        });
                return true;
            }

            //Just show the transaction
            plugin.getCurrenciesManager().getExchange().getTransaction(accountID, transactionId)
                    .thenAccept(transaction -> {
                        if (transaction == null) {
                            plugin.langs().send(sender, plugin.langs().noTransactionFound.replace("%player%", target));

                        } else {
                            sendTransaction(sender, transactionId, transaction);
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
            return plugin.getCurrenciesManager().getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return List.of("numeric_id");
        } else if (args.length == 3) {
            return List.of("revert");
        }
        return List.of();
    }


}
