package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.utils.DecimalUtils;
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

import static dev.unnm3d.rediseconomy.redis.RedisKeys.MSG_CHANNEL;

@AllArgsConstructor
public class PayCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;
    private final HashMap<String, Long> cooldowns = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.langs().send(sender, plugin.langs().noConsole);
            return true;
        }
        if (args.length < 2) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }

        if (cooldowns.getOrDefault(p.getName(), 0L) > System.currentTimeMillis() - plugin.settings().payCooldown) {
            plugin.langs().send(sender, plugin.langs().payCooldown);
            return true;
        }
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis() - plugin.settings().payCooldown);
        cooldowns.put(p.getName(), System.currentTimeMillis());


        if (args.length == 2) {
            payCurrency(p, currenciesManager.getDefaultCurrency(), args);
        } else {
            if (!sender.hasPermission("rediseconomy.pay." + args[2])) {
                plugin.langs().send(sender, plugin.langs().noPermission);
                return true;
            }
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
        final String target = args[0];
        final double amount = currenciesManager.formatAmountString(sender.getName(), currency, args[1]);
        if (amount <= 0) {
            plugin.langs().send(sender, plugin.langs().invalidAmount);
            return;
        }
        if (amount < plugin.settings().minPayAmount) {
            plugin.langs().send(sender, plugin.langs().tooSmallAmount);
            return;
        }
        if (target.equalsIgnoreCase(sender.getName())) {
            plugin.langs().send(sender, plugin.langs().paySelf);
            return;
        } else if (target.equals("*")) {
            if (sender.hasPermission("rediseconomy.payall")) {
                payCurrencyAll(sender, currency, amount);
            } else {
                plugin.langs().send(sender, plugin.langs().noPermission);
            }
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
                        .replace("%amount_short%", DecimalUtils.shortAmount(amount, currency.getDecimalFormat()) +
                                (amount == 1 ? currency.getCurrencySingular() : currency.getCurrencyPlural()))
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

    /**
     * Pay to all online players
     *
     * @param sender   Player who sends the payment
     * @param currency Currency to pay
     * @param amount   Amount to pay
     */
    private void payCurrencyAll(Player sender, Currency currency, double amount) {
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer.getName().equals(sender.getName())) continue;

            if (currenciesManager.isAccountLocked(onlinePlayer.getUniqueId(), sender.getUniqueId())) {
                plugin.langs().send(sender, plugin.langs().blockedPayment.replace("%player%", onlinePlayer.getName()));
                continue;
            }

            final EconomyResponse response = currency.payPlayer(sender.getName(), onlinePlayer.getName(), amount);
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
                            .replace("%amount_short%", DecimalUtils.shortAmount(amount, currency.getDecimalFormat()) +
                                    (amount == 1 ? currency.getCurrencySingular() : currency.getCurrencyPlural()))
                            .replace("%player%", onlinePlayer.getName())
                            .replace("%tax_percentage%", (currency.getTransactionTax() * 100) + "%")
                            .replace("%tax_applied%", currency.format(currency.getTransactionTax() * amount))
            );
            //Send msg to target
            currenciesManager.getRedisManager().getConnectionAsync(commands -> {
                commands.publish(MSG_CHANNEL.toString(), sender.getName() + ";;" + onlinePlayer.getName() + ";;" + currency.format(amount));
                //Register transaction
                return currenciesManager.getExchange().savePaymentTransaction(sender.getUniqueId(), onlinePlayer.getUniqueId(), amount, currency, "Payment to all online players");
            });
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            if(plugin.settings().tabOnlinePLayers){
                return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase()))
                        .toList();
            }
            return currenciesManager.getNameUniqueIds().keySet().stream()
                    .filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase()))
                    .toList();
        } else if (args.length == 2) {
            return List.of("1");
        } else if (args.length == 3) {
            return currenciesManager.getCurrencies().stream().map(Currency::getCurrencyName).filter(name -> name.startsWith(args[2]) && sender.hasPermission("rediseconomy.pay." + args[2])).toList();
        }
        return List.of();
    }


}
