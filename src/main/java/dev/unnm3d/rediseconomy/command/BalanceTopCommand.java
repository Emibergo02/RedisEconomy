package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.jedis.resps.Tuple;
import dev.unnm3d.rediseconomy.RedisEconomy;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@AllArgsConstructor
public class BalanceTopCommand implements CommandExecutor {
    private final RedisEconomy economy;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        economy.getAccountsRedis().thenApply(balances->{
            if (balances.size() > 10)
                balances = balances.subList(0, 10);
            return balances;
        }).thenAccept(balances -> {
            RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP);
            int i = 1;
            for (Tuple t : balances) {
                if (i > 10) break;
                RedisEconomyPlugin.settings().send(sender, RedisEconomyPlugin.settings().BALANCE_TOP_FORMAT.replace("%pos%", i + "").replace("%player%", economy.getPlayerName(UUID.fromString(t.getElement()))).replace("%balance%", t.getScore() + ""));
                i++;
            }
        });
        return true;
    }
}
