package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class BrowseTransactionsCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) return true;
        String target = args[0];
        UUID targetUUID = currenciesManager.getUUIDFromUsernameCache(target);
        if (targetUUID == null) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().playerNotFound);
            return true;
        }

        currenciesManager.getExchange().getTransactions(targetUUID).thenAccept(transactions -> {
            long init = System.currentTimeMillis();
            if (transactions.size() == 0) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noTransactionFound.replace("%player%", target));
                return;
            }
            String afterDateString = "anytime";
            String beforeDateString = "anytime";
            if (args.length == 3) {
                afterDateString = args[1];
                beforeDateString = args[2];
            }

            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().transactionsStart.replace("%player%", target).replace("%after%", afterDateString).replace("%before%", beforeDateString));
            for (int i = 0; i < transactions.size(); i++) {
                if (isAfter(transactions.get(i).timestamp, afterDateString) && isBefore(transactions.get(i).timestamp, beforeDateString)) {
                    currenciesManager.getExchange().sendTransaction(sender, i, transactions.get(i), afterDateString + " " + beforeDateString);
                }
                if (RedisEconomyPlugin.settings().debug)
                    sender.sendMessage("Time: " + (System.currentTimeMillis() - init));
            }

            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().transactionsEnd.replace("%player%", target).replace("%time%", String.valueOf(System.currentTimeMillis() - init)));
        });

        return true;
    }

    private boolean isAfter(long timestamp, String toParse) {
        if (toParse.equals("anytime")) return true;
        try {
            Date parsedDate = currenciesManager.getExchange().formatDate(toParse);
            return new Date(timestamp).after(parsedDate);
        } catch (Exception e) { //this generic but you can control another types of exception
            e.printStackTrace();
            return false;
        }
    }

    private boolean isBefore(long timestamp, String toParse) {
        if (toParse.equals("anytime")) return true;
        try {
            Date parsedDate = currenciesManager.getExchange().formatDate(toParse);
            return new Date(timestamp).before(parsedDate);
        } catch (Exception e) { //this generic but you can control another types of exception
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 2)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return List.of("^ usage ^", currenciesManager.getExchange().convertTimeWithLocalTimeZome(System.currentTimeMillis() - 86400000) + " " + currenciesManager.getExchange().convertTimeWithLocalTimeZome(System.currentTimeMillis()), "<after the date...> <before the date...>");
        }
        return List.of();
    }


}
