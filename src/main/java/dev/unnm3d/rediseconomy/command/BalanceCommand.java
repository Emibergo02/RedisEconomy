package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public abstract class BalanceCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager economy;

    /**
     * Format /balance [player] [currencyName] set/give/take [amount]
     *
     * @param sender  Source of the command
     * @param command Command which was executed
     * @param label   Alias of the command which was used
     * @param args    Passed command arguments
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Currency defaultCurrency = economy.getDefaultCurrency();
        long init = System.currentTimeMillis();
        if (args.length == 0) {
            selfBalancePlayer(sender, defaultCurrency);
        } else if (args.length == 1) {
            balancePlayer(sender, defaultCurrency, args);
        } else if (args.length == 2) {
            if (!sender.hasPermission("rediseconomy.balance." + args[1])) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noPermission);
                return true;
            }
            Currency currency = economy.getCurrencyByName(args[1]);
            if (currency == null) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().invalidCurrency);
                return true;
            }
            balancePlayer(sender, currency, args);

        } else if (args.length > 3) {
            if (!sender.hasPermission("rediseconomy.admin")) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noPermission);
                return true;
            }
            String target = args[0];
            Currency currency = economy.getCurrencyByName(args[1]);
            if (currency == null) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().invalidCurrency);
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().invalidAmount);
                return true;
            }
            String reasonOrCommand = null;
            if (args.length > 4) {
                reasonOrCommand = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            }
            if (args[2].equalsIgnoreCase("give")) {
                givePlayer(sender, currency, amount, target, reasonOrCommand);
            } else if (args[2].equalsIgnoreCase("take")) {
                if (reasonOrCommand == null)
                    takePlayer(sender, currency, amount, target, null);
                else if (reasonOrCommand.startsWith("/")) {
                    takePlayerWithCommand(sender, currency, amount, target, reasonOrCommand);
                } else {
                    takePlayer(sender, currency, amount, target, reasonOrCommand);
                }

            } else if (args[2].equalsIgnoreCase("set")) {
                setPlayer(sender, currency, amount, target);
            }
        }
        if (RedisEconomyPlugin.settings().debug)
            RedisEconomyPlugin.langs().send(sender, "Command executed in " + (System.currentTimeMillis() - init) + "ms");


        return true;
    }

    protected abstract void selfBalancePlayer(CommandSender sender, Currency currency);

    protected abstract void balancePlayer(CommandSender sender, Currency currency, String[] args);

    protected abstract void takePlayer(CommandSender sender, Currency currency, double amount, String target, String reasonOrCommand);

    protected abstract void takePlayerWithCommand(CommandSender sender, Currency currency, double amount, String target, String command);

    protected abstract void givePlayer(CommandSender sender, Currency currency, double amount, String target, String reason);

    protected abstract void setPlayer(CommandSender sender, Currency currency, double amount, String target);


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 2)
                return List.of();
            long init = System.currentTimeMillis();
            List<String> players = economy.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
            if (RedisEconomyPlugin.settings().debug)
                Bukkit.getLogger().info("Tab complete executed in " + (System.currentTimeMillis() - init) + "ms");
            return players;
        } else if (args.length == 2)
            return economy.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[1]) && sender.hasPermission("rediseconomy.balance." + args[1])).toList();
        else if (args.length == 3)
            return List.of("give", "take", "set");
        else if (args.length == 4)
            return List.of("69");
        return List.of();
    }


}
