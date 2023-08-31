package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import lombok.AllArgsConstructor;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.MSG_CHANNEL;

@AllArgsConstructor
public class PayCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.langs().send(sender, plugin.langs().noConsole);
            return true;
        }
        Player p = (Player) sender;
        if (args.length < 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        if (args.length == 2) {
            payCurrency(p, currenciesManager.getDefaultCurrency(), args);
        } else {
            if (!sender.hasPermission("rediseconomy.pay." + args[2]))
                plugin.langs().send(sender, plugin.langs().noPermission);
            Currency currency = currenciesManager.getCurrencyByName(args[2]);
            if (currency == null) {
                plugin.langs().send(sender, plugin.langs().invalidCurrency);
                return true;
            }
            payCurrency(p, currency, args);
        }


        return true;
    }

    private void payCurrency(Player sender, Currency currency, String[] args) {
        if (!sender.hasPermission("rediseconomy.pay")) {
            plugin.langs().send(sender, plugin.langs().noPermission);
            return;
        }
        long init = System.currentTimeMillis();
        String target = args[0];
        double amount = plugin.langs().formatAmountString(args[1]);
        if (amount <= 0) {
            plugin.langs().send(sender, plugin.langs().invalidAmount);
            return;
        }
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.langs().send(sender, plugin.langs().paySelf);
            return;
        }
        if (!currency.hasAccount(target)) {
            plugin.langs().send(sender, plugin.langs().playerNotFound);
            return;
        }
        //If the player has an account uuid is not null
        UUID targetUUID = Objects.requireNonNull(currenciesManager.getUUIDFromUsernameCache(target));

        if (currenciesManager.isAccountLocked(targetUUID, sender.getUniqueId())) {
            plugin.langs().send(sender, plugin.langs().blockedPayment.replace("%player%", target));
            return;
        }

        EconomyResponse response = currency.payPlayer(sender.getName(), target, amount);
        if (!response.transactionSuccess()) {
            if (response.errorMessage.equals("Insufficient funds")) {
                plugin.langs().send(sender, plugin.langs().insufficientFunds);
            } else {
                plugin.langs().send(sender, plugin.langs().payFail);
            }
            return;
        }
        //Send msg to sender
        plugin.langs().send(sender,
                plugin.langs().paySuccess
                        .replace("%amount%", currency.format(amount))
                        .replace("%player%", target)
                        .replace("%tax_percentage%", (currency.getTransactionTax() * 100) + "%")
                        .replace("%tax_applied%", currency.format(currency.getTransactionTax() * amount))
        );
        //Send msg to target
        currenciesManager.getRedisManager().getConnectionAsync(commands -> {
            commands.publish(MSG_CHANNEL.toString(), sender.getName() + ";;" + target + ";;" + currency.format(amount));
            if (plugin.settings().debug) {
                Bukkit.getLogger().info("02 Pay msg sent in " + (System.currentTimeMillis() - init) + "ms. current timestamp" + System.currentTimeMillis());
            }
            //Register transaction
            String reason = "Payment";
            if (args.length >= 4) {
                reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            }
            return currenciesManager.getExchange().savePaymentTransaction(sender.getUniqueId(), targetUUID, amount, currency, reason);
        });

    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return Collections.emptyList();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).collect(Collectors.toList());
        } else if (args.length == 2)
            return Collections.singletonList("69");
        else if (args.length == 3)
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[2]) && sender.hasPermission("rediseconomy.pay." + args[2])).collect(Collectors.toList());

        return Collections.emptyList();
    }


}
