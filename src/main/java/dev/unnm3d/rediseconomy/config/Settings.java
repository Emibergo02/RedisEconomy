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
    @Comment({"Redis settings",
            "Leave password or user empty if you don't have a password or user",})
    public RedisSettings redis = new RedisSettings("localhost", 6379, "", "", 0, 2000, "RedisEconomy");
    @Comment({"All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @Comment({"How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 0;
    @Comment("Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";
    @Comment("Currencies")
    public List<CurrencySettings> currencies = List.of(new CurrencySettings("vault", "euro", "euros", 0, 0, true), new CurrencySettings("dollar", "$", "$", 0, 0, false));

    public record CurrencySettings(String currencyName, String currencySingle, String currencyPlural,
                                   double startingBalance, double payTax, boolean bankEnabled) {
    }

    public record RedisSettings(String host, int port, String user,String password, int database, int timeout, String clientName) {
    }
}
