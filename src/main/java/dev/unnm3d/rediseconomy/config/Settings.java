package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@Configuration
public class Settings {
    @Comment({"This is automatically generated on server startup",
            "You need to have a different serverId for each server!!!"})
    public String serverId = String.valueOf(UUID.randomUUID());
    @Comment("Language file")
    public String lang = "en-US";
    @Comment("Webeditor URL")
    public String webEditorUrl = "https://webui.advntr.dev/";
    @Comment("Activate this before reporting an issue")
    public boolean debug = false;
    @Comment("If true, the plugin registers who's calling it's methods inside transactions")
    public boolean registerCalls = false;
    @Comment({"if true, migrates the bukkit offline uuids accounts to the default RedisEconomy currency",
            "During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public boolean migrationEnabled = false;
    @Comment({"if enabled, the plugin will migrate economy data from sql database",
            "During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public SqlMigrateSettings sqlMigration = new SqlMigrateSettings(false, "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/database?useSSL=false", "root", "password", "economy", "name", "uuid", "money");
    @Comment("Allow paying with percentage (ex: /pay player 10% sends 'player' 10% of the sender balance)")
    public boolean allowPercentagePayments = true;
    @Comment({"Leave password or user empty if you don't have a password or user",
            "Don't use the default credentials in production!! Generate new credentials on RedisLabs -> https://github.com/Emibergo02/RedisEconomy/wiki/Install-redis",
            "Default credentials lead to a non-persistent redis server, only for testing!!",
    })
    public RedisSettings redis = new RedisSettings("localhost", 6379, "", "", 0, 2000, "RedisEconomy");
    @Comment({"All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @Comment({"How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 0;
    @Comment("Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";
    @Comment("Cooldown between payments in milliseconds")
    public int payCooldown = 500;
    @Comment("Minimum amount of money that can be paid")
    public double minPayAmount = 0.01;
    @Comment({"Currencies", "payTax is the tax on payments, 0.1 = 10% tax"})
    public List<CurrencySettings> currencies = List.of(new CurrencySettings("vault", "euro", "euros", "#.##", "en-US", 0, Double.POSITIVE_INFINITY, 0, true, true, false), new CurrencySettings("dollar", "$", "$", "#.##", "en-US", 0, Double.POSITIVE_INFINITY, 0, false, false, false));

    public record CurrencySettings(String currencyName, String currencySingle, String currencyPlural,
                                   String decimalFormat, String languageTag,
                                   double startingBalance, double maxBalance, double payTax,
                                   boolean saveTransactions, boolean bankEnabled, boolean taxOnlyPay) {
    }

    public record RedisSettings(String host, int port, String user, String password, int database, int timeout,
                                String clientName) {
    }

    public record SqlMigrateSettings(
            @Comment("Enable or not SQL migration")
            boolean enabled,
            @Comment("Sql driver to use, this is not mandatory due just make a safe-check before initializing the connection")
            String driver,
            @Comment("Connection URL, replace 'localhost:3306' and 'database' with your database information")
            String url,
            @Comment("Connection username")
            String username,
            @Comment("Connection password")
            String password,
            @Comment("This is the table on SQL database to get data from")
            String table,
            @Comment("Column name inside provided table to get player name")
            String nameColumn,
            @Comment("Column name inside provided table to get player unique id")
            String uuidColumn,
            @Comment("Column name inside provided table to get player balance")
            String moneyColumn) {
    };
}
