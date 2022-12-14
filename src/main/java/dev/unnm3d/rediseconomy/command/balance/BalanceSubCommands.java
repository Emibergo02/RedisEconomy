package dev.unnm3d.rediseconomy.command.balance;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceSubCommands extends BalanceCommand {
    private final RedisEconomyPlugin plugin;

    public BalanceSubCommands(CurrenciesManager economy, RedisEconomyPlugin plugin) {
        super(economy);
        this.plugin = plugin;
    }

    @Override
    protected void balancePlayer(CommandSender sender, Currency currency, String[] args) {
        String target = args[0];
        if (!currency.hasAccount(target)) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().playerNotFound);
            return;
        }
        RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balanceOther.replace("%balance%", String.valueOf(currency.format(currency.getBalance(target)))).replace("%player%", target));
    }

    @Override
    protected void selfBalancePlayer(CommandSender sender, Currency currency) {
        if (!(sender instanceof Player p)) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noConsole);
            return;
        }
        RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balance.replace("%balance%", String.valueOf(currency.format(currency.getBalance(p)))));
    }

    @Override
    protected void takePlayer(CommandSender sender, Currency currency, double amount, String target, String reasonOrCommand) {
        EconomyResponse er = currency.withdrawPlayer(target, amount, reasonOrCommand);
        if (er.transactionSuccess())
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void takePlayerWithCommand(CommandSender sender, Currency currency, double amount, String target, String command) {
        EconomyResponse er = currency.withdrawPlayer(target, amount, command);
        if (er.transactionSuccess()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.replace("%player%", target).replace("%amount%", String.format("%.2f", amount)).substring(1));
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        } else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void givePlayer(CommandSender sender, Currency currency, double amount, String target, String reason) {
        EconomyResponse er = currency.depositPlayer(target, amount, reason);
        if (er.transactionSuccess())
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }

    @Override
    protected void setPlayer(CommandSender sender, Currency currency, double amount, String target) {
        EconomyResponse er = currency.setPlayerBalance(target, amount);
        if (er.transactionSuccess())
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().balanceSet.replace("%balance%", currency.format(er.balance)).replace("%player%", target));
        else sender.sendMessage(er.errorMessage);
    }
}
