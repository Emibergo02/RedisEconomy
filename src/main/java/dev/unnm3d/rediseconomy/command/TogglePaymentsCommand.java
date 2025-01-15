package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
public class TogglePaymentsCommand implements CommandExecutor, TabCompleter {
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.langs().send(sender, plugin.langs().noConsole);
            return true;
        }

        if (args.length == 0) {
            List<UUID> localLocked = plugin.getCurrenciesManager().getLockedAccounts(p.getUniqueId());
            if (localLocked.contains(RedisKeys.getAllAccountUUID())) {
                plugin.langs().send(sender, plugin.langs().blockedAccounts
                        .replace("%list%", "all"));
                return true;
            }
            plugin.langs().send(sender, plugin.langs().blockedAccounts
                    .replace("%list%",
                            localLocked.stream()
                                    .map(plugin.getCurrenciesManager()::getUsernameFromUUIDCache)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining(", "))));
            return true;
        }

        UUID targetUUID = RedisKeys.getAllAccountUUID();
        if (!args[0].equals("*") && !args[0].equals("all")) {
            targetUUID = plugin.getCurrenciesManager().getUUIDFromUsernameCache(args[0]);
        }
        if (targetUUID == null) {
            plugin.langs().send(sender, plugin.langs().playerNotFound);
            return true;
        }
        plugin.getCurrenciesManager().toggleAccountLock(p.getUniqueId(), targetUUID)
                .thenAccept(isLocked -> {
                    if (isLocked) {
                        plugin.langs().send(sender, plugin.langs().blockedAccountSuccess
                                .replace("%player%", args[0]));
                    } else {
                        plugin.langs().send(sender, plugin.langs().unblockedAccountSuccess
                                .replace("%player%", args[0]));
                    }
                });


        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].length() < plugin.settings().tab_complete_chars)
                return List.of();
            return plugin.getCurrenciesManager().getNameUniqueIds().keySet().stream().filter(name -> name.toUpperCase().startsWith(args[0].toUpperCase())).toList();
        }

        return List.of();
    }


}
