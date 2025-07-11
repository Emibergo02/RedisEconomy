package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@AllArgsConstructor
public class ArchiveTransactionsCommand implements CommandExecutor, TabCompleter {
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        if (args[0].contains("..") || args[0].startsWith(File.pathSeparator)) {
            plugin.langs().send(sender, plugin.langs().invalidPath);
            return true;
        }

        Path userPath = Path.of(plugin.getDataFolder().getAbsolutePath(), args[0]);

        plugin.getCurrenciesManager().getExchange().archiveTransactions(sender, userPath);

        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of("backup.rediseco");
    }
}
