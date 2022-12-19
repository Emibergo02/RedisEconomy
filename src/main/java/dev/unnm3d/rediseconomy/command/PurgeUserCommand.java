package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
public class PurgeUserCommand implements CommandExecutor, TabCompleter {

    private final CurrenciesManager currenciesManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return true;
        String target = args[0];
        boolean onlyNameUUID = false;
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("onlyNameUUID")) {
                onlyNameUUID = true;
            }
        }
        Map<String, UUID> nameUUIDs = currenciesManager.removeNamePattern(target, !onlyNameUUID);
        if (nameUUIDs.size() == 0) {
            RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().playerNotFound);
            return true;
        }
        RedisEconomyPlugin.langs().send(sender, RedisEconomyPlugin.langs().purgeUserSuccess.replace("%player%", target));


        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < 2)
                return List.of();
            return currenciesManager.getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        } else if (args.length == 2) {
            return List.of("onlyNameUUID");
        }
        return null;
    }
}
