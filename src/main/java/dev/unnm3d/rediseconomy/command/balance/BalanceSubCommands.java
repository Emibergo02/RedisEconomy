package dev.unnm3d.rediseconomy.command.balance;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.utils.DecimalUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BalanceSubCommands extends BalanceCommand {

    public BalanceSubCommands(CurrenciesManager economy, RedisEconomyPlugin plugin) {
        super(economy, plugin);
    }

    @Override
    protected void balancePlayer(CommandSender sender, Currency currency, String target) {
        if (!currency.hasAccount(target)) {
            plugin.langs().send(sender, plugin.langs().playerNotFound);
            return;
        }
        double currentBalance = currency.getBalance(target);
        plugin.langs().send(sender, plugin.langs().balanceOther
                .replace("%balance%", String.valueOf(currency.format(currentBalance)))
                .replace("%balance_short%", DecimalUtils.shortAmount(currentBalance, currency.getDecimalFormat()) +
                        (currentBalance == 1 ? currency.getCurrencySingular() : currency.getCurrencyPlural()))
                .replace("%player%", target));
    }

    @Override
    protected void selfBalancePlayer(CommandSender sender, Currency currency) {
        if (!(sender instanceof Player p)) {
            plugin.langs().send(sender, plugin.langs().noConsole);
            return;
        }

        plugin.langs().send(sender, plugin.langs().balance.replace("%balance_short%",
                        DecimalUtils.shortAmount(currency.getBalance(p), currency.getDecimalFormat()) +
                                (currency.getBalance(p) == 1 ? currency.getCurrencySingular() : currency.getCurrencyPlural()))
                .replace("%balance%", String.valueOf(currency.format(currency.getBalance(p)))));
    }

    @Override
    protected void takePlayer(CommandSender sender, Currency currency, double amount, String target, String reasonOrCommand) {
        EconomyResponse er = currency.withdrawPlayer(target, amount, reasonOrCommand);
        if (er.transactionSuccess())
            plugin.langs().send(sender, plugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void takePlayerWithCommand(CommandSender sender, Currency currency, double amount, String target, String command) {
        EconomyResponse er = currency.withdrawPlayer(target, amount, command);
        if (er.transactionSuccess()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.replace("%player%", target).replace("%amount%", currency.getDecimalFormat().format(amount)).substring(1));
            plugin.langs().send(sender, plugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        } else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void givePlayer(CommandSender sender, Currency currency, double amount, String target, String reason) {
        if (target.equals("*") && sender.hasPermission("rediseconomy.admin.giveall")) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                EconomyResponse er = currency.depositPlayer(p.getUniqueId(), p.getName(), amount, reason);
                if (er.transactionSuccess())
                    plugin.langs().send(sender, plugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", p.getName()));
                else sender.sendMessage(er.errorMessage);
            }
            return;
        }
        EconomyResponse er = currency.depositPlayer(target, amount, reason);
        if (er.transactionSuccess())
            plugin.langs().send(sender, plugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void setPlayer(CommandSender sender, Currency currency, double amount, String target) {
        EconomyResponse er = currency.setPlayerBalance(target, amount);
        if (er.transactionSuccess())
            plugin.langs().send(sender, plugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void setPlayerMaxBalance(CommandSender sender, Currency currency, double amount, String target) {
        final UUID targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(target);
        if (targetUUID == null) {
            plugin.langs().send(sender, plugin.langs().playerNotFound);
            return;
        }
        currency.setPlayerMaxBalance(targetUUID, amount);
        plugin.langs().send(sender, plugin.langs().maxBalanceSet.replace("%player%", target).replace("%amount%", currency.format(amount)));
    }

}
