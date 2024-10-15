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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class BackupRestoreCommand implements CommandExecutor, TabCompleter {
    private static final long MAX_MEMORY_USAGE = 10L * 1024 * 1024 * 1024;
    private static final int MEMORY_CHUNK_USAGE = 3;
    private static final int LINE_SIZE_ESTIMATE = 130;

    private final CurrenciesManager currenciesManager;
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
        CompletableFuture.runAsync(() -> {
            Path userPath = Path.of(plugin.getDataFolder().getAbsolutePath(), args[0]);
            switch (label) {
                case "backup-economy" -> {
                    final Map<UUID, String> names = new HashMap<>();
                    for (Map.Entry<String, UUID> entry : currenciesManager.getNameUniqueIds().entrySet()) {
                        names.put(entry.getValue(), entry.getKey());
                    }

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(userPath.normalize().toFile(), true))) {
                        long memory = Math.min(Runtime.getRuntime().freeMemory(), MAX_MEMORY_USAGE);
                        int chunkSize = (int) (memory / MEMORY_CHUNK_USAGE) / LINE_SIZE_ESTIMATE;

                        for (Currency currency : currenciesManager.getCurrencies()) {
                            Map<UUID, Double> accounts = currency.getAccounts();
                            plugin.getLogger().info("[" + currency.getCurrencyName() + "] Total accounts: " + accounts.size());
                            if (accounts.isEmpty()) {
                                continue;
                            }

                            final Iterator<Map.Entry<UUID, Double>> iterator = accounts.entrySet().iterator();
                            int i;
                            for (i = 0; i < accounts.size(); i += chunkSize) {
                                final StringBuilder chunk = new StringBuilder();

                                int end = Math.min(i + chunkSize, accounts.size());
                                for (int j = i; j < end; j++) {
                                    final Map.Entry<UUID, Double> entry = iterator.next();
                                    final UUID uuid = entry.getKey();
                                    final double balance = entry.getValue();
                                    // currency;uuid;name;balance
                                    chunk.append(currency.getCurrencyName())
                                            .append(";")
                                            .append(uuid.toString())
                                            .append(";")
                                            .append(names.getOrDefault(uuid, "null"))
                                            .append(";")
                                            .append(balance)
                                            .append(System.lineSeparator());
                                }

                                writer.write(chunk.toString());
                                writer.flush();

                                plugin.getLogger().info("[" + currency.getCurrencyName() + "] Progress: " + i + "/" + accounts.size());
                            }

                            plugin.getLogger().info("[" + currency.getCurrencyName() + "] Progress: " + Math.min(i + chunkSize, accounts.size()) + "/" + accounts.size());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                case "restore-economy" -> {
                    try (FileInputStream is = new FileInputStream(userPath.normalize().toFile())) {
                        List<String> lines = new BufferedReader(new InputStreamReader(is)).lines().toList();
                        HashMap<String, ArrayList<ScoredValue<String>>> accounts = new HashMap<>();
                        HashMap<String, String> nameUUIDs = new HashMap<>();
                        //Put every information in a data structure
                        for (String line : lines) {
                            String[] split = line.split(";");
                            if (accounts.computeIfPresent(split[0], (s, scoredValues) -> {
                                scoredValues.add(ScoredValue.just(Double.parseDouble(split[3]), split[1]));
                                return scoredValues;
                            }) == null) {
                                accounts.put(split[0], new ArrayList<>(List.of(ScoredValue.just(Double.parseDouble(split[3]), split[1]))));
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
