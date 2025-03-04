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
    @Comment("If true, the plugin registers who's calling it's methods inside transactions")
    public boolean registerCalls = false;
    @Comment("List of regex to be excluded from the registerCalls")
    public List<String> callBlacklistRegex = List.of("^org\\.bukkit.*","^io\\.papermc.*", "^dev\\.unnm3d\\.rediseconomy.*","^com\\.mojang.*");
    @Comment({"if true, migrates the bukkit offline uuids accounts to the default RedisEconomy currency",
            "During the migration, the plugin will be disabled. Restart all RedisEconomy instances after the migration."})
    public boolean migrationEnabled = false;
    @Comment("Allow paying with percentage (ex: /pay player 10% sends 'player' 10% of the sender balance)")
    public boolean allowPercentagePayments = true;
    @Comment({"Leave password or user empty if you don't have a password or user",
            "Don't use the default credentials in production!! Generate new credentials on RedisLabs -> https://github.com/Emibergo02/RedisEconomy/wiki/Install-redis",
            "Default credentials lead to a non-persistent redis server, only for testing!!",
    })
    public RedisSettings redis = new RedisSettings("localhost", 6379, "", "", 0, 300, "RedisEconomy", 5, 3);
    @Comment({"All RedisEconomy instances with the same cluster id will share the same data"})
    public String clusterId = "";
    @Comment({"How many chars are needed for a command autocompletion", "Increase if you have a lot of players to list"})
    public int tab_complete_chars = 0;
    @Comment("If true, the tab completion will show only online players. tab_complete_chars is applied")
    public boolean tabOnlinePlayers = false;
    @Comment("Default currency name (must be the same as the currency name in the currencies list)")
    public String defaultCurrencyName = "vault";
    @Comment("Cooldown between payments in milliseconds")
    public int payCooldown = 500;
    @Comment("Minimum amount of money that can be paid")
    public double minPayAmount = 0.01;
    @Comment({"Currencies", "payTax is the tax on payments, 0.1 = 10% tax"})
    public List<CurrencySettings> currencies = List.of(new CurrencySettings("vault", "euro", "euros", "#.##", "en-US", 0, 100000000000000d, 0, true, true, false), new CurrencySettings("dollar", "$", "$", "#.##", "en-US", 0, 100000000000000d, 0, false, false, false));

    public record CurrencySettings(String currencyName, String currencySingle, String currencyPlural,
                                   String decimalFormat, String languageTag,
                                   double startingBalance, double maxBalance, double payTax,
                                   boolean saveTransactions, boolean bankEnabled, boolean taxOnlyPay) {
    }

    public record RedisSettings(String host, int port, String user, String password, int database, int timeout,
                                String clientName, int poolSize, int tryAgainCount) {
        //Those checks are for new config files, if the user doesn't have the new settings
        public int getPoolSize() {
            return poolSize == 0 ? 5 : poolSize;
        }

        public int getTryAgainCount() {
            return tryAgainCount == 0 ? 3 : tryAgainCount;
        }
    }
}
