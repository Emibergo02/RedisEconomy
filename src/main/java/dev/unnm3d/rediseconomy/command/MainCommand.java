package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
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
        } else if (args.length == 1) {
            if (!args[0].equalsIgnoreCase("reload")) return true;
            if (!sender.hasPermission("rediseconomy.admin")) return true;
            plugin.getConfigManager().loadSettingsConfig();//Reload configs
            plugin.getConfigManager().loadLangs(); //Reload langs
            plugin.getConfigManager().saveConfigs(); //Save configs
            this.adventureWebuiEditorAPI = new AdventureWebuiEditorAPI(plugin.getConfigManager().getSettings().webEditorUrl);
            sender.sendMessage("§aReloaded successfully!");
            return true;
        }
        String langField = args[1];

        if (args[0].equalsIgnoreCase("expandpool")) {
            if (!sender.hasPermission("rediseconomy.admin.expandpool")) {
                plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().noPermission);
                return true;
            }
            try {
                plugin.getCurrenciesManager().getRedisManager().expandPool(Integer.parseInt(args[1]));
                plugin.getConfigManager().getLangs().send(sender, "§aPool expanded successfully!");
            } catch (Exception e) {
                plugin.getConfigManager().getLangs().send(sender, "§cError expanding pool: " + e.getMessage());
            }
            return true;
        }

        if (!sender.hasPermission("rediseconomy.admin.editmessage")) {
            plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().noPermission);
            return true;
        }

        if (args[0].equalsIgnoreCase("savemessage")) {
            if (args.length < 3) return true;
            adventureWebuiEditorAPI.retrieveSession(args[2]).thenAccept(message -> {
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
        } else if (args[0].equalsIgnoreCase("editmessage")) {
            try {
                String fieldString = plugin.getConfigManager().getLangs().getStringFromField(langField);
                if (fieldString == null) {
                    plugin.getConfigManager().getLangs().send(sender, plugin.getConfigManager().getLangs().editMessageError);
                    return true;
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
        return true;
    }


    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "editmessage", "expandpool");
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
