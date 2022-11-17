package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@AllArgsConstructor
public class TransactionCommand implements CommandExecutor, TabCompleter {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
    private final CurrenciesManager currenciesManager;
    private final EconomyExchange exchange;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) return true;
        String target = args[0];
        long init = System.currentTimeMillis();
        exchange.getTransactions(target).thenAccept(transactions -> {
            if (transactions.length == 0) {
                sender.sendMessage("§cNo transactions found for player " + target);
                return;
            }
            String afterDateString = "anytime";
            String beforeDateString = "anytime";
            if (args.length == 3) {
                afterDateString = args[1];
                beforeDateString = args[2];
            }

            sender.sendMessage("§3Transactions of player " + target + ":");
            for (EconomyExchange.Transaction t : transactions) {
                if (isAfter(t.timestamp, afterDateString) && isBefore(t.timestamp, beforeDateString)) {
                    RedisEconomyPlugin.settings().send(sender,
                            RedisEconomyPlugin.settings().TRANSACTION_ITEM
                                    .replace("%amount%", t.amount)
                                    .replace("%sender%", t.sender)
                                    .replace("%receiver%", t.target)
                                    .replace("%timestamp%", convertTimeWithLocalTimeZome(t.timestamp))
                                    .replace("%afterbefore%", afterDateString + " " + beforeDateString)
                    );

                }

            }
            sender.sendMessage("§3End transactions of player " + target + " in " + (System.currentTimeMillis() - init) + "ms");
        });

        return true;
    }

    public String convertTimeWithLocalTimeZome(long time) {
        Date date = new Date(time);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }

    private boolean isAfter(long timestamp, String toParse) {
        if (toParse.equals("anytime")) return true;
        try {
            Date parsedDate = dateFormat.parse(toParse);
            return new Date(timestamp).after(parsedDate);
        } catch (Exception e) { //this generic but you can control another types of exception
            e.printStackTrace();
            return false;
        }
    }

    private boolean isBefore(long timestamp, String toParse) {
        if (toParse.equals("anytime")) return true;
        try {
            Date parsedDate = dateFormat.parse(toParse);
            return new Date(timestamp).before(parsedDate);
        } catch (Exception e) { //this generic but you can control another types of exception
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 3)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.startsWith(args[0])).toList();
        } else if (args.length == 2) {
            if (args[1].trim().equals(""))
                return List.of("^ usage ^", convertTimeWithLocalTimeZome(System.currentTimeMillis() - 86400000) + " " + convertTimeWithLocalTimeZome(System.currentTimeMillis()), "<after the date...> <before the date...>");
        }


        return List.of();
    }


}
