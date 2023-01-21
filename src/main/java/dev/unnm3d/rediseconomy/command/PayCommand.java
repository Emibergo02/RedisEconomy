package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.MSG_CHANNEL;

@AllArgsConstructor
public class PayCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noConsole);
            return true;
        }
        if (args.length < 2) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().missingArguments);
            return true;
        }
        if (args.length == 2) {
            payDefaultCurrency(p, currenciesManager.getDefaultCurrency(), args);
        } else {
            if (!sender.hasPermission("rediseconomy.pay." + args[2]))
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noPermission);
            Currency currency = currenciesManager.getCurrencyByName(args[2]);
            if (currency == null) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().invalidCurrency);
                return true;
            }
            payDefaultCurrency(p, currency, args);
        }


        return true;
    }

    private void payDefaultCurrency(Player sender, Currency currency, String[] args) {
        if (!sender.hasPermission("rediseconomy.pay")) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().noPermission);
            return;
        }
        long init = System.currentTimeMillis();
        String target = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().invalidAmount);
            return;
        }
        if (target.equalsIgnoreCase(sender.getName())) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().paySelf);
            return;
        }
        if (!currency.hasAccount(target)) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().playerNotFound);
            return;
        }
        //If the player has an account uuid is not null
        UUID targetUUID = Objects.requireNonNull(currenciesManager.getUUIDFromUsernameCache(target));

        EconomyResponse response = currency.payPlayer(sender.getName(), target, amount);
        if (!response.transactionSuccess()) {
            if (response.errorMessage.equals("Insufficient funds")) {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().insufficientFunds);
            } else {
                RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().payFail);
            }
            return;
        }
        //Send msg to sender
        RedisEconomyPlugin.langs().send(sender,
                RedisEconomyPlugin.langs().paySuccess
                        .replace("%amount%", currency.format(amount))
                        .replace("%player%", target)
                        .replace("%tax_percentage%", (currency.getTransactionTax() * 100) + "%")
                        .replace("%tax_applied%", currency.format(currency.getTransactionTax() * amount))
        );
        //Send msg to target
        currenciesManager.getRedisManager().getConnection(connection -> {
            RedisAsyncCommands<String, String> commands = connection.async();
            commands.publish(MSG_CHANNEL.toString(), sender.getName() + ";;" + target + ";;" + currency.format(amount));
            if (RedisEconomyPlugin.settings().debug) {
                Bukkit.getLogger().info("02 Pay msg sent in " + (System.currentTimeMillis() - init) + "ms. current timestamp" + System.currentTimeMillis());
            }
            //Register transaction
            String reason = "Payment";
            if (args.length >= 4) {
                reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            }
            currenciesManager.getExchange().savePaymentTransaction(commands, sender.getUniqueId(), targetUUID, amount, currency, reason);
            return null;
        });

    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 2)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2)
            return List.of("69");
        else if (args.length == 3)
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[2]) && sender.hasPermission("rediseconomy.pay." + args[2])).toList();

        return List.of();
    }


}
