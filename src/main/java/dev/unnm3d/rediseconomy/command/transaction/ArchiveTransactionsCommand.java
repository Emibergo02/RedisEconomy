package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

        CompletableFuture.runAsync(() -> {
            try (FileWriter fw = new FileWriter(userPath.normalize().toFile())) {
                StringBuilder sb = new StringBuilder();
                int progress = 0;
                for (Map.Entry<String, UUID> entry : plugin.getCurrenciesManager().getNameUniqueIds().entrySet()) {
                    progress++;
                    try {
                        AccountID accountID = new AccountID(entry.getValue());
                        Collection<Transaction> transactions = plugin.getCurrenciesManager().getExchange().getTransactions(accountID,Integer.MAX_VALUE)
                                .toCompletableFuture().get()
                                .values();
                        if (transactions.isEmpty())
                            continue;
                        sb.append(entry.getKey()).append(";").append(entry.getValue()).append(System.lineSeparator()); // name;uuid
                        transactions.forEach(transaction ->
                                sb.append(transaction.toString()).append(System.lineSeparator()) // "serialized" transaction
                        );
                        sb.append(System.lineSeparator()); // empty line between accounts
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    if (progress % 100 == 0)
                        plugin.langs().send(sender, plugin.langs().transactionsArchiveProgress.replace("%progress%", String.valueOf(progress * 100 / plugin.getCurrenciesManager().getNameUniqueIds().size())));
                }
                fw.write(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        }).thenAccept(aVoid ->
                plugin.getCurrenciesManager().getExchange().removeAllTransactions().thenAccept(deletedTransactionAccounts ->
                        plugin.langs().send(sender, plugin.langs().transactionsArchiveCompleted
                                .replace("%size%", deletedTransactionAccounts.toString())
                                .replace("%file%", userPath.toFile().getName()))));


        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of("backup.rediseco");
    }
}
