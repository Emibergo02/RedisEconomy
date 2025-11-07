package dev.unnm3d.rediseconomy.command;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.CurrencyWithBanks;
import dev.unnm3d.rediseconomy.redis.RedisKeys;
import dev.unnm3d.rediseconomy.utils.AdventureWebuiEditorAPI;
import lombok.AllArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;


@AllArgsConstructor
public class MainCommand implements CommandExecutor, TabCompleter {
    private RedisEconomyPlugin plugin;
    private AdventureWebuiEditorAPI adventureWebuiEditorAPI;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.getConfigManager().getLangs().send(sender, plugin.langs().missingArguments);
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("rediseconomy.admin")) {
                reloadPlugin(sender);
                return true;
            }
            if (args[0].equalsIgnoreCase("test")) {
                if (sender.hasPermission("rediseconomy.admin.test")) {
                    long init = System.currentTimeMillis();
                    sender.sendMessage("§6Processing 10000 transactions...");
                    CurrencyWithBanks defaultBankCurrency = (CurrencyWithBanks) plugin.getCurrenciesManager().getDefaultCurrency();
                    for (int j = 0; j < 10; j++) {

                        final String accountId = "test12345678912" + j;
                        defaultBankCurrency.createBank(accountId, sender.getName());
                        double shouldBeBalance = 0;
                        for (int i = 0; i < 1000; i++) {
                            if (i % 100 == 0) {
                                defaultBankCurrency.setBankBalance(accountId, 0);
                                shouldBeBalance = 0;
                            }
                            defaultBankCurrency.bankDeposit(accountId, i, "Test" + i + " " + j);
                            shouldBeBalance += i;
                        }
                        double finalShouldBeBalance = shouldBeBalance;
                        new UniversalRunnable() {
                            @Override
                            public void run() {
                                plugin.getCurrenciesManager().getRedisManager().getConnectionAsync(connection ->
                                                connection.zscore(RedisKeys.BALANCE_BANK_PREFIX + defaultBankCurrency.getCurrencyName(), accountId))
                                        .thenAccept(balance -> {
                                            sender.sendMessage("§bBank " + accountId + " balance -> remote: " + balance + " local: " + finalShouldBeBalance + " in " + (System.currentTimeMillis() - init) + "ms");
                                            if (balance == finalShouldBeBalance) {
                                                this.cancel();
                                            }
                                        });
                            }
                        }.runTaskTimerAsynchronously(plugin, 10, 10);
                    }
                } else {
                    plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().noPermission);
                }
            }
            return true;
        }

        String langField = args[1];

        if (args[0].equalsIgnoreCase("expandpool")) {
            if (sender.hasPermission("rediseconomy.admin.expandpool")) {
                expandPool(sender, args[1]);
            } else {
                plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().noPermission);
            }
            return true;
        }


        if (!sender.hasPermission("rediseconomy.admin.editmessage")) {
            plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().noPermission);
            return true;
        }

        if (args[0].equalsIgnoreCase("savemessage") && args.length >= 3) {
            saveMessage(sender, langField, args[2]);
        } else if (args[0].equalsIgnoreCase("editmessage")) {
            editMessage(sender, langField);
        }

        return true;
    }

    private void reloadPlugin(CommandSender sender) {
        plugin.getConfigManager().loadSettingsConfig();
        plugin.getConfigManager().loadLangs();
        plugin.getConfigManager().saveConfigs();
        plugin.getCurrenciesManager().loadCurrencySystem();
        plugin.loadPlayerList();
        this.adventureWebuiEditorAPI = new AdventureWebuiEditorAPI(plugin.getConfigManager().getSettings().webEditorUrl);
        sender.sendMessage("§aReloaded successfully!");
    }

    private void expandPool(CommandSender sender, String arg) {
        try {
            plugin.getCurrenciesManager().getRedisManager().expandPool(Integer.parseInt(arg));
            plugin.getConfigManager().getLangs().send(sender, "§aPool expanded successfully!");
        } catch (Exception e) {
            plugin.getConfigManager().getLangs().send(sender, "§cError expanding pool: " + e.getMessage());
        }
    }

    private void saveMessage(CommandSender sender, String langField, String token) {
        adventureWebuiEditorAPI.retrieveSession(token).thenAccept(message -> {
            try {
                if (plugin.getConfigManager().getLangs().setStringField(langField, message)) {
                    plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageSuccess.replace("%field%", langField));
                    plugin.getConfigManager().saveConfigs();
                } else {
                    plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageError);
                }
            } catch (IllegalAccessException | NoSuchFieldException e) {
                plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageError);
            }
        });
    }

    private void editMessage(CommandSender sender, String langField) {
        try {
            String fieldString = plugin.getConfigManager().getLangs().getStringFromField(langField);
            if (fieldString == null) {
                plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageError);
                return;
            }
            adventureWebuiEditorAPI.startSession(fieldString, "/rediseconomy savemessage " + langField + " {token}", "RedisEconomy")
                    .thenAccept(token ->
                            plugin.getConfigManager().getLangs()
                                    .send(sender, plugin.getConfigManager().getLangs().editMessageClickHere
                                            .replace("%field%", langField)
                                            .replace("%url%", adventureWebuiEditorAPI.getEditorUrl(token))
                                    )
                    );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageError);
        }
    }


    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "editmessage", "expandpool", "test");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("expandpool")) {
            return List.of("1", "2", "3", "4", "5");
        } else if (args.length == 2 && sender.hasPermission("rediseconomy.admin.editmessage") && args[0].equalsIgnoreCase("editmessage")) {
            return Arrays.stream(plugin.getConfigManager().getLangs().getClass().getFields())
                    .filter(field -> field.getType().equals(String.class))
                    .map(Field::getName)
                    .filter(name -> name.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
