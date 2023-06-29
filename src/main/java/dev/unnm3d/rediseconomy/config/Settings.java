package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@Configuration
public class Settings {
    @Comment({"This is automatically generated on server startup",
            "Change it only if you have disabled plugin messages on the proxy"})
    public String serverId = UUID.randomUUID() + "";
    @Comment("Language file")
    public String lang = "en-US";
    @Comment("Webeditor URL")
    public String webEditorUrl = "https://webui.advntr.dev/";
    @Comment("Activate this before reporting an issue")
    public boolean debug = false;
    @Comment({"if true, migrates the bukkit offline uuids accounts to the default RedisEconomy currency",
            "During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public boolean migrationEnabled = false;
    @Comment({"Leave password or user empty if you don't have a password or user",
            "Don't use the default credentials in production!! Generate new credentials on RedisLabs -> https://github.com/Emibergo02/RedisEconomy/wiki/Install-redis",
            "Default credentials lead to a non-persistent redis server, only for testing!!",
    })
    public RedisSettings redis = new RedisSettings("redis-14919.c293.eu-central-1-1.ec2.cloud.redislabs.com", 14919, "default", "XNlI2IaLV04lm29AK3trpquHcScku9z2", 0, 2000, "RedisEconomy");
    @Comment({"All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @Comment({"How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 0;
    @Comment("Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";
    @Comment("Currencies")
    public List<CurrencySettings> currencies = List.of(new CurrencySettings("vault", "euro", "euros", "#.##", "en-US", 0, 0, true), new CurrencySettings("dollar", "$", "$", "#.##", "en-US", 0, 0, false));

    public record CurrencySettings(String currencyName, String currencySingle, String currencyPlural,
                                   String decimalFormat, String languageTag,
                                   double startingBalance, double payTax, boolean bankEnabled) {
    }

    public record RedisSettings(String host, int port, String user, String password, int database, int timeout,
                                String clientName) {
    }
}
