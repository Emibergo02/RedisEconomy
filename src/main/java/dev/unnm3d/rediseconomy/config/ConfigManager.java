package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import lombok.Getter;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private final RedisEconomyPlugin plugin;
    @Getter
    private Settings settings;
    @Getter
    private Langs langs;

    private static final YamlConfigurationProperties PROPERTIES = YamlConfigurationProperties.newBuilder()
            .header(
                    """
                            ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                            ┃      RedisEconomy Config     ┃
                            ┃      Developed by Unnm3d     ┃
                            ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                            """
            )
            .footer("Authors: Unnm3d")
            .charset(StandardCharsets.UTF_8)
            .build();

    public ConfigManager(RedisEconomyPlugin plugin) {
        this.plugin = plugin;
        loadSettingsConfig();
    }

    public void loadSettingsConfig() {
        File settingsFile = new File(plugin.getDataFolder(), "config.yml");
        settings = YamlConfigurations.update(
                settingsFile.toPath(),
                Settings.class,
                PROPERTIES
        );
        if (settings.redis.tryAgainCount() < 2 || settings.redis.poolSize() < 2)
            plugin.getLogger().severe("Please regenerate the redis configuration section. New settings have been added.");
    }

    public void saveConfigs() {
        YamlConfigurations.save(new File(plugin.getDataFolder(), "config.yml").toPath(), Settings.class, settings, PROPERTIES);
        YamlConfigurations.save(new File(plugin.getDataFolder(), settings.lang + ".yml").toPath(), Langs.class, langs, PROPERTIES);
    }

    public void loadLangs() {
        File settingsFile = new File(plugin.getDataFolder(), settings.lang + ".yml");
        if (!settingsFile.exists()) {
            plugin.saveResource("it-IT.yml", false);
            plugin.saveResource("de-DE.yml", false);
            plugin.saveResource("zh-CN.yml", false);
            plugin.saveResource("fr-FR.yml", false);
            plugin.saveResource("es-ES.yml", false);
        }
        langs = YamlConfigurations.update(
                settingsFile.toPath(),
                Langs.class,
                PROPERTIES
        );
    }

}
