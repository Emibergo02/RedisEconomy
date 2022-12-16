package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.config.ConfigManager;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@AllArgsConstructor
public class MainCommand implements CommandExecutor, TabCompleter {
    private ConfigManager configManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rediseconomy.admin")) return true;
            String serverId = configManager.getSettings().serverId;
            configManager.loadConfigs();
            configManager.getSettings().serverId = serverId;
            sender.sendMessage("§aReloaded successfully " + configManager.getSettings().serverId + "!");
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return null;
    }
}
