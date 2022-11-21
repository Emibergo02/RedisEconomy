package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.transaction.EconomyExchange;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.unnm3d.rediseconomy.redis.RedisKeys.MSG_CHANNEL;

@AllArgsConstructor
public class PayCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager economy;
    private final EconomyExchange exchange;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_CONSOLE);
            return true;
        }
        if (args.length == 2) {
            payDefaultCurrency(p, economy.getDefaultCurrency(), args);
        } else if (args.length == 3) {
            if (!sender.hasPermission("rediseconomy.pay." + args[2]))
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_PERMISSION);
            Currency currency = economy.getCurrencyByName(args[2]);
            payDefaultCurrency(p, currency, args);
        }


        return true;
    }

    private void payDefaultCurrency(Player sender, Currency currency, String[] args) {
        if (!sender.hasPermission("rediseconomy.pay")) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().NO_PERMISSION);
            return;
        }
        long init = System.currentTimeMillis();
        String target = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INVALID_AMOUNT);

            return;
        }
        //if (target.equalsIgnoreCase(sender.getName())) {
        //    RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().PAY_SELF);
        //    return;
        //}
        if (!currency.hasAccount(target)) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().PLAYER_NOT_FOUND);
            return;
        }
        //If the player has an account uuid is not null
        UUID targetUUID = Objects.requireNonNull(economy.getUUIDFromUsernameCache(target));

        if (!currency.withdrawPlayer(sender, amount).transactionSuccess()) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().INSUFFICIENT_FUNDS);
            return;
        }

        if (!currency.depositPlayer(target, amount).transactionSuccess()) {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().PAY_FAIL);
            return;
        }

        //Send msg to sender
        RedisEconomyPlugin.settings().send(sender,
                RedisEconomyPlugin.settings().PAY_SUCCESS
                        .replace("%amount%", currency.format(amount))
                        .replace("%player%", target)
                        .replace("%tax_percentage%", (currency.getTransactionTax() * 100) + "%")
                        .replace("%tax_applied%", currency.format(currency.getTransactionTax() * amount))
        );
        //Send msg to target
        economy.getRedisManager().getConnection(connection -> {
            RedisAsyncCommands<String, String> commands = connection.async();
            commands.publish(MSG_CHANNEL.toString(), sender.getName() + ";;" + target + ";;" + currency.format(amount));
            if (RedisEconomyPlugin.settings().DEBUG) {
                Bukkit.getLogger().info("02 Pay msg sent in " + (System.currentTimeMillis() - init) + "ms. current timestamp" + System.currentTimeMillis());
            }
            //Register transaction
            exchange.saveTransaction(commands, sender.getUniqueId(), targetUUID, amount);
            return null;
        });

    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 3)
                return List.of();
            return economy.getNameUniqueIds().keySet().stream().filter(name -> name.startsWith(args[0])).toList();
        } else if (args.length == 2)
            return List.of("69");
        else if (args.length == 3)
            return economy.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[2]) && sender.hasPermission("rediseconomy.pay." + args[2])).toList();

        return List.of();
    }


}
