package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;

@SuppressWarnings("unused")
@Configuration
public class Settings {
    @Comment("Language file")
    public String lang = "en-US";
    @Comment("Webeditor URL")
    public String webEditorUrl = "https://webui.advntr.dev/";
    @Comment("Activate this before reporting an issue")
    public boolean debug = false;
    @Comment("A specific debug for cache update")
    public boolean debugUpdateCache = false;
    @Comment({"If 0 registerCalls is disabled, if 1 it will log only the calling class",
            "if 2 it will log the calling class and method, if 3 it will log the calling class, method and line number"})
    public int registerCallsVerbosity = 0;
    @Comment("List of regex to be excluded from the registerCalls")
    public List<String> callBlacklistRegex = List.of("^org\\.bukkit.*", "^io\\.papermc.*", "^dev\\.unnm3d\\.rediseconomy.*", "^com\\.mojang.*");
    @Comment({"if true, migrates the bukkit offline uuids accounts to the default RedisEconomy currency",
            "During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public boolean migrationEnabled = false;
    @Comment("Allow paying with percentage (ex: /pay player 10% sends 'player' 10% of the sender balance)")
    public boolean allowPercentagePayments = true;
    @Comment({"Leave password or user empty if you don't have a password or user",
            "Don't use the default credentials in production!! Generate new credentials on RedisLabs -> https://github.com/Emibergo02/RedisEconomy/wiki/Install-redis",
            "Default credentials lead to a non-persistent redis server, only for testing!!",
    })
    public RedisSettings redis = new RedisSettings("localhost", 6379, "", "", 0, 1000, "RedisEconomy", false, 10, 3);
    @Comment({"All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @Comment({"How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 0;
    @Comment("If true, the tab completion will show only online players. tab_complete_chars is applied")
    public boolean tabOnlinePlayers = false;
    @Comment("Enable baltop hide permissions, if true /balancetop toggle will be overridden by permissions on join")
    public boolean enableHidePermissions = false;
    @Comment("Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";
    @Comment("Cooldown between payments in milliseconds")
    public int payCooldown = 500;
    @Comment("Minimum amount of money that can be paid")
    public double minPayAmount = 0.01;
    @Comment("Placeholder cache update interval in milliseconds")
    public int placeholderCacheUpdateInterval = 5000;
    @Comment("How many accounts to show in the %rediseco_top_number...% placeholder")
    public int baltopPlaceholderAccounts = 100;
    @Comment("Fixed pool size for making transactions")
    public int transactionExecutorThreads = 3;
    @Comment({"Currency name must be less than 8 characters",
            "Decimal format is for displaying currency amounts, e.g. '#,##0.00'. More info: https://www.baeldung.com/java-decimalformat",
            "Language tag is for the decimal format, to use . or , as decimal separator, e.g. 'en-US' for . and 'de-DE' for ,",
            "Pay tax is the tax that will be applied to all payments, e.g. 0.05 for 5%",
            "Save transactions is for saving transactions in the database, if false, transactions are not saved",
            "Transaction TTL is the time after which a transaction is considered expired and removed (THIS ONLY WORKS ON REDIS 7.4+)",
            "Bank enable is to enable the bank feature on Vault API Economy class (which is extended by Currency class)",
            "Executor threads are the number of threads to use for executing balance updates on this currency (optimal is 3)"
    })
    public List<CurrencySettings> currencies = List.of(new CurrencySettings("vault", "euro", "euros", "#.##", "en-US", 0, 100000000000000d, 0, true, -1, true, false, 3), new CurrencySettings("dollar", "$", "$", "#.##", "en-US", 0, 100000000000000d, 0, false, -1, false, false, 2));


    public record RedisSettings(String host, int port, String user, String password, int database, int timeout,
                                String clientName, boolean ssl, int poolSize, int tryAgainCount) {
        //Those checks are for new config files, if the user doesn't have the new settings
        public int getPoolSize() {
            return poolSize == 0 ? 5 : poolSize;
        }

        public int getTryAgainCount() {
            return tryAgainCount == 0 ? 3 : tryAgainCount;
        }
    }
}
