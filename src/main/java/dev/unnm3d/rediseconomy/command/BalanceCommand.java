package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import lombok.AllArgsConstructor;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@AllArgsConstructor
public class BalanceCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager economy;

    /**
     * Format /balance [player] [currencyName] set/give/take [amount]
     * @param sender Source of the command
     * @param command Command which was executed
     * @param label Alias of the command which was used
     * @param args Passed command arguments
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Currency defaultCurrency=economy.getDefaultCurrency();
        long init = System.currentTimeMillis();
        if (args.length == 0) {
            selfBalancePlayer(sender, defaultCurrency);
        } else if (args.length == 1) {
            balancePlayer(sender, defaultCurrency, args);
        }else if(args.length==2){
            if(!sender.hasPermission("rediseconomy.balance."+args[1])){
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_PERMISSION);
                return true;
            }
            Currency currency=economy.getCurrency(args[1]);
            balancePlayer(sender, currency, args);

        }else if (args.length == 4) {
            if (!sender.hasPermission("rediseconomy.admin")) {
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_PERMISSION);
                return true;
            }
            String target = args[0];
            Currency currency = economy.getCurrency(args[1]);
            double amount;
            try {
                amount = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INVALID_AMOUNT);
                return true;
            }

            if (args[2].equalsIgnoreCase("give")) {
                EconomyResponse er = currency.depositPlayer(target, amount);
                if (er.transactionSuccess())
                    RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
                else sender.sendMessage(er.errorMessage);
            } else if (args[2].equalsIgnoreCase("take")) {
                EconomyResponse er = currency.withdrawPlayer(target, amount);
                if (er.transactionSuccess())
                    RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
                else sender.sendMessage(er.errorMessage);
            } else if (args[2].equalsIgnoreCase("set")) {
                EconomyResponse er = currency.setPlayerBalance(target, amount);
                if (er.transactionSuccess())
                    RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
                else sender.sendMessage(er.errorMessage);
            }
        }
        if(RedisEconomyPlugin.settings().DEBUG)
            RedisEconomyPlugin.settings().send(sender, "§aCommand executed in " + (System.currentTimeMillis() - init) + "ms");



        return true;
    }
    private void selfBalancePlayer(CommandSender sender, Currency currency){
        if (!(sender instanceof Player p)) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_CONSOLE);
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_CONSOLE);
            return;
        }
        RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE.replace("%balance%", String.valueOf(currency.format(currency.getBalance(p)))));
    }
    private void balancePlayer(CommandSender sender, Currency currency, String[] args){
        String target = args[0];
        if (!currency.hasAccount(target)) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().PLAYER_NOT_FOUND);
            return;
        }
        RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_OTHER.replace("%balance%", String.valueOf(currency.format(currency.getBalance(target)))).replace("%player%", target));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return economy.getNameUniqueIds().keySet().stream().filter(name -> name.startsWith(args[0])).toList();
        else if (args.length == 2)
            return economy.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[1])&& sender.hasPermission("rediseconomy.balance."+args[1])).toList();
        else if (args.length == 3)
            return List.of("give", "take", "set");
        else if (args.length == 4)
            return List.of("69");
        return List.of();
    }
}
