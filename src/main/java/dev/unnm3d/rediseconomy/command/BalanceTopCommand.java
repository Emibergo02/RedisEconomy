package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.jedis.resps.Tuple;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.vaultcurrency.RedisEconomy;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.UUID;

@AllArgsConstructor
public class BalanceTopCommand implements CommandExecutor {
    private final RedisEconomy economy;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        final int page;
        if (args.length == 1) {
            page = Integer.getInteger(args[0], 1);
        } else {
            page = 1;
        }
        economy.getAccountsRedis().thenApply(balances -> {
            if (balances.size() < (page - 1) * 10) {
                return new ArrayList<Tuple>();
            } else if (balances.size() > page * 10)
                balances = balances.subList((page - 1) * 10, page * 10);
            else balances = balances.subList((page - 1) * 10, balances.size());
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP.replace("%page%", String.valueOf(page))
                    .replace("%nextpage%", "<click:run_command:/balancetop " + (balances.size() <= page * 10 ? 1 : page + 1) + ">-></click>")
                    .replace("%prevpage%", "<click:run_command:/balancetop " + (page == 1 ? (balances.size() % 10) + 1 : page - 1) + "><-</click>"));
            return balances;
        }).thenAccept(balances -> {

            int i = 1;
            for (Tuple t : balances) {
                if (i > 10) break;
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP_FORMAT.replace("%pos%", i + "").replace("%player%", economy.getPlayerName(UUID.fromString(t.getElement()))).replace("%balance%", economy.format(t.getScore())));
                i++;
            }
        });
        return true;
    }
}
