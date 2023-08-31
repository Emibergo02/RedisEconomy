package dev.unnm3d.rediseconomy.config;

import dev.unnm3d.rediseconomy.config.struct.CurrencySettings;
import dev.unnm3d.rediseconomy.config.struct.RedisSettings;
import ru.xezard.configurations.Configuration;
import ru.xezard.configurations.ConfigurationComments;
import ru.xezard.configurations.ConfigurationField;

import java.io.File;
import java.util.*;

@ConfigurationComments({
        "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓",
        "┃      RedisEconomy Config     ┃",
        "┃      Developed by Unnm3d     ┃",
        "┃        Edited by vSKAH       ┃",
        "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"})
public class Settings extends Configuration {

    public Settings(File folder) {
        super(folder.getAbsolutePath() + File.separator + "config.yml");
    }
    @ConfigurationField("serverId")
    @ConfigurationComments({"# This is automatically generated on server startup", "# Change it only if you have disabled plugin messages on the proxy"})
    public String serverId = UUID.randomUUID().toString();

    @ConfigurationField("lang")
    @ConfigurationComments("# Language file")
    public String lang = "en-US";
    @ConfigurationField("webEditorUrl")
    @ConfigurationComments("# Webeditor URL")
    public String webEditorUrl = "https://webui.advntr.dev/";
    @ConfigurationField("debug")
    @ConfigurationComments("# Activate this before reporting an issue")
    public boolean debug = false;
    @ConfigurationField("migrationEnabled")
    @ConfigurationComments({"# if true, migrates the bukkit offline uuids accounts to the default RedisEconomy currency",
            "# During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public boolean migrationEnabled = false;
    @ConfigurationComments({"# Leave password or user empty if you don't have a password or user",
            "# Don't use the default credentials in production!! Generate new credentials on RedisLabs -> https://github.com/Emibergo02/RedisEconomy/wiki/Install-redis",
            "# Default credentials lead to a non-persistent redis server, only for testing!!",
    })

    @ConfigurationField("redis")
    public RedisSettings redis = new RedisSettings("redis-14919.c293.eu-central-1-1.ec2.cloud.redislabs.com", 14919, "default", "XNlI2IaLV04lm29AK3trpquHcScku9z2", 0, 2000, "RedisEconomy");

    @ConfigurationField("clusterId")
    @ConfigurationComments({"# All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @ConfigurationField("tab_complete_chars")
    @ConfigurationComments({"# How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 1;
    @ConfigurationField("defaultCurrencyName")
    @ConfigurationComments("# Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";

    @ConfigurationField("currencies")
    @ConfigurationComments("# Currencies")
    public List<CurrencySettings> currencies = Arrays.asList(new CurrencySettings("vault", "euro", "euros", "#.##", "en-US", 0, 0, true), new CurrencySettings("dollar", "$", "$", "#.##", "en-US", 0, 0, false));

}
