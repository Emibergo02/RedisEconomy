package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


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
            int transactionId = Integer.parseInt(args[1]);

            boolean revertTransaction = args.length > 2 && args[2].equalsIgnoreCase("revert");

            UUID targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(target);
            AccountID accountID = targetUUID != null ? new AccountID(targetUUID) : new AccountID(target);
            if (revertTransaction) {
                if (plugin.settings().debug)
                    Bukkit.getLogger().info("revert00 Reverting transaction " + transactionId + " called by " + sender.getName());
                plugin.getCurrenciesManager().getExchange().revertTransaction(accountID, transactionId)
                        .thenAccept(newId -> {
                            sender.sendMessage("§3Transaction reverted with #" + newId);
                            if (newId == -1)
                                sender.sendMessage("§3Transaction reverted with §cFAIL");
                        });
                return true;
            }
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
                return Collections.emptyList();
            return plugin.getCurrenciesManager().getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return Collections.singletonList( "numeric_id");
        } else if (args.length == 3) {
            return Collections.singletonList("revert");
        }
        return Collections.emptyList();
    }


}
