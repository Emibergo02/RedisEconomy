package dev.unnm3d.rediseconomy.config;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.struct.TransactionItem;
import dev.unnm3d.rediseconomy.config.struct.UnitSymbols;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import ru.xezard.configurations.Configuration;
import ru.xezard.configurations.ConfigurationField;

import java.lang.reflect.Field;

public final class Langs extends Configuration {
    private final BukkitAudiences audiences = BukkitAudiences.create(RedisEconomyPlugin.getInstance());
    @ConfigurationField("noConsole")
    public String noConsole = "<red>You must be in-game to use this command!</red>";
    @ConfigurationField("noPermission")
    public String noPermission = "<red>You do not have permission to use this command!</red>";
    @ConfigurationField("missingArguments")
    public String missingArguments = "<red>Missing arguments!</red>";
    @ConfigurationField("playerNotFound")
    public String playerNotFound = "<red>Player not found!</red>";
    @ConfigurationField("invalidAmount")
    public String invalidAmount = "<red>Invalid amount!</red>";
    @ConfigurationField("invalidCurrency")
    public String invalidCurrency = "<red>Invalid currency!</red>";
    @ConfigurationField("insufficientFunds")
    public String insufficientFunds = "<red>You do not have enough money!</red>";
    @ConfigurationField("balance")
    public String balance = "<green>You have %balance%!</green>";
    @ConfigurationField("balanceSet")
    public String balanceSet = "<green>You set %player% account to %balance% !</green>";
    @ConfigurationField("balanceOther")
    public String balanceOther = "<green>%player% has %balance% !</green>";
    @ConfigurationField("balanceTop")
    public String balanceTop = "<green>Top richest players:</green><br>%prevpage%      %page%      %nextpage%";
    @ConfigurationField("blockedAccounts")
    public String blockedAccounts = "<green>Blocked accounts:</green><br>%list%";
    @ConfigurationField("blockedAccountSuccess")
    public String blockedAccountSuccess = "<green>Account %player% has been blocked!</green>";
    @ConfigurationField("unblockedAccountSuccess")
    public String unblockedAccountSuccess = "<green>Account %player% has been unblocked!</green>";
    @ConfigurationField("blockedPayment")
    public String blockedPayment = "<red>Your payments to %player% have been blocked!</red>";
    @ConfigurationField("balanceTopFormat")
    public String balanceTopFormat = "<aqua>%pos% - %player% %balance%</aqua>";
    @ConfigurationField("paySelf")
    public String paySelf = "<red>You cannot pay yourself!</red>";
    @ConfigurationField("paySuccess")
    public String paySuccess = "<green>You paid %player% %amount% with %tax_percentage% (%tax_applied%) of transaction fee!</green>";
    @ConfigurationField("payFail")
    public String payFail = "<red>Payment failed!</red>";
    @ConfigurationField("payReceived")
    public String payReceived = "<green>You received %amount% from %player%!</green>";
    @ConfigurationField("purgeUserSuccess")
    public String purgeUserSuccess = "<green>Users matching %player% have been purged!</green>";
    @ConfigurationField("purgeBalanceSuccess")
    public String purgeBalanceSuccess = "<green>Users matching %player% have been reset for currency %currency%!</green>";
    @ConfigurationField("switchCurrencySuccess")
    public String switchCurrencySuccess = "<green>Switched %currency% to %switch-currency%.<br>Please restart immediately every instance<br> with RedisEconomy installed to avoid any overwrite!</green>";
    @ConfigurationField("noTransactionFound")
    public String noTransactionFound = "<red>No transaction found for %player%!</red>";
    @ConfigurationField("incorrectDate")
    public String incorrectDate = "<red>Incorrect Date formatting !</red>";
    @ConfigurationField("purgeTransactionsSuccess")
    public String purgeTransactionsSuccess = "<green>Purged %size% transactions from %player% before %date%</green>";
    @ConfigurationField("transactionsStart")
    public String transactionsStart = "<dark_aqua>Transactions of player %player% from %after% to %before%!</dark_aqua>";
    @ConfigurationField("transactionsEnd")
    public String transactionsEnd = "<dark_aqua>End of %player% transactions in %time% ms</dark_aqua>";
    @ConfigurationField("transactionsArchiveCompleted")
    public String transactionsArchiveCompleted = "<green>Archived %size% transaction accounts to %file%</green>";
    @ConfigurationField("transactionsArchiveProgress")
    public String transactionsArchiveProgress = "<aqua>Archiving progress: %progress%% </aqua>";
    @ConfigurationField("editMessageError")
    public String editMessageError = "<red>This config entry is not a String or doesn't exist!";
    @ConfigurationField("editMessageClickHere")
    public String editMessageClickHere = "<click:open_url:%url%>Click here to edit the message %field%!</click>";
    @ConfigurationField("editMessageSuccess")
    public String editMessageSuccess = "<green>Saved successfully %field%!";
    @ConfigurationField("transactionItem")
    public TransactionItem transactionItem = new TransactionItem(
            "<dark_aqua>#%id%</dark_aqua> <click:copy_to_clipboard:%timestamp%><hover:show_text:\"<blue>Click to copy:</blue><br><aqua>%timestamp%</aqua>\"><gold>[Timestamp⌛]</hover></click> <click:run_command:/transaction %account-owner% %id% revert><hover:show_text:\"Click to revert transaction\"><red>[←Revert]</hover></click><br>" +
                    "<click:run_command:/transaction %account-owner% %afterbefore%><dark_green>%account-owner%</dark_green></click> <grey>> <white>%amount%%symbol% <grey>></grey> <click:run_command:/transaction %other-account% %afterbefore%><green>%other-account%</green></click><br>" +
                    "<yellow>Reason: </yellow>%reason%",
            "<dark_aqua>#%id%</dark_aqua> <click:copy_to_clipboard:%timestamp%><hover:show_text:\"<blue>Click to copy:</blue><br><aqua>%timestamp%</aqua>\"><gold>[Timestamp⌛]</hover></click> <click:run_command:/transaction %account-owner% %id% revert><hover:show_text:\"Click to revert transaction\"><red>[←Revert]</hover></click><br>" +
                    "<click:run_command:/transaction %other-account% %afterbefore%><green>%other-account%</green></click> <grey>> <white>%amount%%symbol% <grey>></grey> <click:run_command:/transaction %account-owner% %afterbefore%><dark_green>%account-owner%</dark_green></click><br>" +
                    "<yellow>Reason: </yellow>%reason%");
    @ConfigurationField("unitSymbols")
    public UnitSymbols unitSymbols = new UnitSymbols("k", "m", "b", "t", "q");
    @ConfigurationField("backupRestoreFinished")
    public String backupRestoreFinished = "<green>Backup/restore file %file% finished!</green>";

    public Langs(String pathToFile) {
        super(pathToFile);
    }

    public void send(CommandSender sender, String text) {
        audiences.sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(text));
    }

    public double formatAmountString(String amount) {
        try {
            if (amount.endsWith(unitSymbols.getQuadrillion())) {
                return Double.parseDouble(amount.substring(0, amount.length() - unitSymbols.getQuadrillion().length())) * 1_000_000_000_000_000D;
            } else if (amount.endsWith(unitSymbols.getTrillion())) {
                return Double.parseDouble(amount.substring(0, amount.length() - unitSymbols.getTrillion().length())) * 1_000_000_000_000D;
            } else if (amount.endsWith(unitSymbols.getBillion())) {
                return Double.parseDouble(amount.substring(0, amount.length() - unitSymbols.getBillion().length())) * 1_000_000_000D;
            } else if (amount.endsWith(unitSymbols.getMillion())) {
                return Double.parseDouble(amount.substring(0, amount.length() - unitSymbols.getMillion().length())) * 1_000_000D;
            } else if (amount.endsWith(unitSymbols.getThousand())) {
                return Double.parseDouble(amount.substring(0, amount.length() - unitSymbols.getThousand().length())) * 1_000D;
            }
            return Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public @Nullable Field getStringField(String name) throws NoSuchFieldException {
        Field field = getClass().getField(name);
        return field.getType().equals(String.class) ? field : null;
    }

    public @Nullable String getStringFromField(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = getStringField(fieldName);
        return field != null ? (String) field.get(this) : null;
    }

    public boolean setStringField(String fieldName, String text) throws NoSuchFieldException, IllegalAccessException {
        Field field = getStringField(fieldName);
        if (field == null) return false;
        field.set(this, text);
        return true;
    }

}
