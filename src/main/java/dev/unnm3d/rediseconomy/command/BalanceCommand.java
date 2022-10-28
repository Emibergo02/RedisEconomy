package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomy;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
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
    private final RedisEconomy economy;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().NO_CONSOLE));
                return true;
            }
            sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().BALANCE.replace("%balance%", String.valueOf(economy.format(economy.getBalance(p))))));
        } else if (args.length == 1) {
            String target = args[0];
            if (!economy.hasAccount(target)) {
                sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().PLAYER_NOT_FOUND));
                return true;
            }
            sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().BALANCE_OTHER.replace("%balance%", String.valueOf(economy.format(economy.getBalance(target)))).replace("%player%", target)));
        } else if (args.length == 3) {
            if (!sender.hasPermission("rediseconomy.admin")) {
                sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().NO_PERMISSION));
                return true;
            }
            String target = args[0];
            if (!economy.hasAccount(target)) {
                sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().PLAYER_NOT_FOUND));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().INVALID_AMOUNT));
                return true;
            }

            if (args[1].equalsIgnoreCase("give")) {
                EconomyResponse er = economy.depositPlayer(target, amount);
                if (er.transactionSuccess())
                    sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", economy.format(er.balance)).replace("%player%", target)));
                else sender.sendMessage(er.errorMessage);
            } else if (args[1].equalsIgnoreCase("take")) {
                EconomyResponse er = economy.withdrawPlayer(target, amount);
                if (er.transactionSuccess())
                    sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", economy.format(er.balance)).replace("%player%", target)));
                else sender.sendMessage(er.errorMessage);
            } else if (args[1].equalsIgnoreCase("set")) {
                EconomyResponse er = economy.setPlayerBalance(target, amount);
                if (er.transactionSuccess())
                    sender.sendMessage(RedisEconomyPlugin.settings().parse(RedisEconomyPlugin.settings().BALANCE_SET.replace("%balance%", economy.format(er.balance)).replace("%player%", target)));
                else sender.sendMessage(er.errorMessage);
            }
        }


        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return economy.getNameUniqueIds().keySet().stream().toList();
        else if (args.length == 2)
            return List.of("give", "take", "set");
        else if (args.length == 3)
            return List.of("69");
        return List.of();
    }
}
