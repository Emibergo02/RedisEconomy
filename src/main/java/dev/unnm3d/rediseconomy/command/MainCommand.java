package dev.unnm3d.rediseconomy.command;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.ConfigManager;
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
    private ConfigManager configManager;
    private AdventureWebuiEditorAPI adventureWebuiEditorAPI;


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            configManager.getLangs().send(sender, RedisEconomyPlugin.getInstance().langs().missingArguments);
            return true;
        }
        if (args.length == 1) {
            if (!args[0].equalsIgnoreCase("reload")) return true;
            if (!sender.hasPermission("rediseconomy.admin")) return false;
            String serverId = configManager.getSettings().serverId;
            configManager.loadConfigs();
            configManager.getSettings().serverId = serverId;
            sender.sendMessage("§aReloaded successfully " + configManager.getSettings().serverId + "!");
        }
        String langField = args[1];

        if (!sender.hasPermission("rediseconomy.admin.editmessage")) return false;


        if (args[0].equalsIgnoreCase("savemessage")) {
            if (args.length < 3) return true;
            adventureWebuiEditorAPI.retrieveSession(args[2]).thenAccept(message -> {
                try {
                    if (configManager.getLangs().setStringField(langField, message)) {
                        configManager.getLangs().send(sender, configManager.getLangs().editMessageSuccess.replace("%field%", langField));
                        configManager.saveConfigs();
                    } else {
                        configManager.getLangs().send(sender, configManager.getLangs().editMessageError);
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    configManager.getLangs().send(sender, configManager.getLangs().editMessageError);
                }
            });
        } else if (args[0].equalsIgnoreCase("editmessage")) {
            try {
                String fieldString = configManager.getLangs().getStringFromField(langField);
                if (fieldString == null) {
                    configManager.getLangs().send(sender, configManager.getLangs().editMessageError);
                    return false;
                }
                adventureWebuiEditorAPI.startSession(fieldString, "/rediseconomy savemessage " + langField + " {token}", "RedisEconomy")
                        .thenAccept(token -> configManager.getLangs().send(sender, configManager.getLangs().editMessageClickHere.replace("%field%", langField).replace("%url%", adventureWebuiEditorAPI.getEditorUrl(token))));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                configManager.getLangs().send(sender, configManager.getLangs().editMessageError);
            }
        }
        return true;
    }


    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "editmessage");
        } else if (args.length == 2 && sender.hasPermission("rediseconomy.admin.editmessage")) {
            return Arrays.stream(configManager.getLangs().getClass().getFields()).filter(field -> field.getType().equals(String.class)).map(Field::getName).toList();
        }
        return List.of();
    }
}
