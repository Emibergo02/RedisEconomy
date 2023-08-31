package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@AllArgsConstructor
public class BackupRestoreCommand implements CommandExecutor, TabCompleter {
    private final CurrenciesManager currenciesManager;
    private final RedisEconomyPlugin plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.langs().send(sender, plugin.langs().missingArguments);
            return true;
        }
        if(args[0].contains("..")||args[0].startsWith(File.pathSeparator)){
            plugin.langs().send(sender, plugin.langs().invalidPath);
            return true;
        }
        CompletableFuture.runAsync(() -> {
            Path userPath= Path.of(plugin.getDataFolder().getAbsolutePath(), args[0]);
            switch (label) {
                case "backup-economy" -> {
                    try (FileWriter fw = new FileWriter(userPath.normalize().toFile())) {
                        StringBuilder sb = new StringBuilder();
                        currenciesManager.getCurrencies().forEach(currency ->
                                currency.getAccounts().forEach((uuid, balance) ->
                                        sb.append(currency.getCurrencyName())
                                                .append(";")
                                                .append(uuid.toString())
                                                .append(";")
                                                .append(currenciesManager.getUsernameFromUUIDCache(uuid))
                                                .append(";")
                                                .append(balance)
                                                .append(System.getProperty("line.separator"))
                                ));
                        fw.write(sb.toString());// currency;uuid;name;balance
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                case "restore-economy" -> {
                    try (FileInputStream is = new FileInputStream(userPath.normalize().toFile())) {
                        List<String> lines = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.toList());
                        HashMap<String, ArrayList<ScoredValue<String>>> accounts = new HashMap<>();
                        HashMap<String, String> nameUUIDs = new HashMap<>();
                        //Put every information in a data structure
                        for (String line : lines) {
                            String[] split = line.split(";");
                            if (accounts.computeIfPresent(split[0], (s, scoredValues) -> {
                                scoredValues.add(ScoredValue.just(Double.parseDouble(split[3]), split[1]));
                                return scoredValues;
                            }) == null) {
                                accounts.put(split[0], new ArrayList<>(Collections.singletonList(ScoredValue.just(Double.parseDouble(split[3]), split[1]))));
                            }

                            nameUUIDs.put(split[2], split[1]);
                        }
                        plugin.getLogger().info(accounts.size() + " currency to restore");

                        //Restore every currency
                        accounts.forEach((currencyName, scoredValues) -> {
                            Currency currency = currenciesManager.getCurrencyByName(currencyName);
                            if (currency == null) {
                                plugin.getLogger().warning("Currency " + currencyName + " not found: restoring skipped");
                            } else {
                                currency.updateBulkAccountsCloudCache(scoredValues, nameUUIDs);
                                plugin.getLogger().info("Restored " + scoredValues.size() + " accounts for currency " + currencyName);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        }).thenAccept(aVoid ->
                plugin.langs().send(sender, plugin.langs().backupRestoreFinished.replace("%file%", args[0]))
        );

        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of("backup.csv");
    }
}
