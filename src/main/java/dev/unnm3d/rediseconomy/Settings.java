package dev.unnm3d.rediseconomy;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class Settings {


    private final BukkitAudiences audiences;
    public String SERVER_ID;
    public boolean DEBUG;
    public int TRANSACTIONS_RETAINED;
    public String NO_CONSOLE;
    public String NO_PERMISSION;
    public String PLAYER_NOT_FOUND;
    public String BALANCE_TOP;
    public String BALANCE_TOP_FORMAT;
    public String BALANCE;
    public String BALANCE_OTHER;
    public String BALANCE_SET;
    public String BALANCE_SET_OTHER;
    public String PAY_SUCCESS;
    public String PAY_SELF;
    public String PAY_FAIL;
    public String PAY_RECEIVED;
    public String TRANSACTION_ITEM;
    public String INVALID_AMOUNT;
    public String INSUFFICIENT_FUNDS;

    public Settings(RedisEconomyPlugin plugin) {
        this.audiences = BukkitAudiences.create(plugin);
        FileConfiguration config = plugin.getConfig();
        this.SERVER_ID = config.getString("server-id", System.currentTimeMillis() + "");
        this.DEBUG= config.getBoolean("debug", false);
        this.TRANSACTIONS_RETAINED = config.getInt("transactions-retained", 200);
        this.NO_CONSOLE = config.getString("lang.no-console", "<red>This command can't be executed from console!");
        this.NO_PERMISSION = config.getString("lang.no-permission", "<red>You don't have permission to execute this command!");
        this.PLAYER_NOT_FOUND = config.getString("lang.player-not-found", "<red>This player doesn't exist!");
        this.BALANCE_TOP = config.getString("lang.balance-top", "<gold>Top 10 richest players:");
        this.BALANCE_TOP_FORMAT = config.getString("lang.balance-top-format", "<gold>%pos%. <white>%player% <gold>with <white>%balance% <gold>coins");
        this.BALANCE = config.getString("lang.balance", "<gold>Your balance is <white>%balance% <gold>coins");
        this.BALANCE_OTHER = config.getString("lang.balance-other", "<gold>%player%'s balance is <white>%balance% <gold>coins");
        this.BALANCE_SET = config.getString("lang.balance-set", "<gold>Your balance has been set to <white>%balance% <gold>coins");
        this.BALANCE_SET_OTHER = config.getString("lang.balance-set", "<gold>%player%'s balance has been set to <white>%balance% <gold>coins");
        this.PAY_SUCCESS = config.getString("lang.pay-success", "<gold>You have paid <white>%player% <gold>%amount% <gold>coins");
        this.PAY_SELF = config.getString("lang.pay-self", "<red>You can't pay yourself!");
        this.PAY_FAIL = config.getString("lang.pay-fail", "<red>You don't have enough money to pay <white>%amount% <gold>coins to <white>%player%");
        this.PAY_RECEIVED = config.getString("lang.pay-received", "<gold>You received <white>%amount% <gold>coins from <white>%player%");
        this.TRANSACTION_ITEM = config.getString("lang.transaction-item", "<aqua>%timestamp%</aqua> <dark_green>%sender%<dark_green> -> <green>%receiver%<green> <gold>%amount%</gold>!");
        this.INVALID_AMOUNT = config.getString("lang.invalid-amount", "<red>Invalid amount!");
        this.INSUFFICIENT_FUNDS = config.getString("lang.insufficient-funds", "<red>You don't have enough money!");
    }

    public void send(CommandSender sender, String text) {
        audiences.sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(text));
    }
}
