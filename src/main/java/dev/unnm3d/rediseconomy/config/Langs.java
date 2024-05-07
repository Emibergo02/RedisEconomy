package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.NavigableMap;
import java.util.TreeMap;

@Configuration
public final class Langs {
    private final BukkitAudiences audiences = BukkitAudiences.create(RedisEconomyPlugin.getInstance());
    public String noConsole = "<red>You must be in-game to use this command!</red>";
    public String noPermission = "<red>You do not have permission to use this command!</red>";
    public String missingArguments = "<red>Missing arguments!</red>";
    public String playerNotFound = "<red>Player not found!</red>";
    public String invalidAmount = "<red>Invalid amount!</red>";
    public String tooSmallAmount = "<red>The amount is too small!</red>";
    public String payCooldown = "<red>Your previous payment is still in progress! Wait please</red>";
    public String invalidCurrency = "<red>Invalid currency!</red>";
    public String insufficientFunds = "<red>You do not have enough money!</red>";
    public String balance = "<green>You have %balance%!</green>";
    public String balanceSet = "<green>You set %player% account to %balance% !</green>";
    public String balanceOther = "<green>%player% has %balance% !</green>";
    public String balanceTop = "<green>Top richest players:</green><br>%prevpage%      %page%      %nextpage%";
    public String blockedAccounts = "<green>Blocked accounts:</green><br>%list%";
    public String blockedAccountSuccess = "<green>Account %player% has been blocked!</green>";
    public String unblockedAccountSuccess = "<green>Account %player% has been unblocked!</green>";
    public String blockedPayment = "<red>Your payments to %player% have been blocked!</red>";
    public String balanceTopFormat = "<aqua>%pos% - %player% %balance%</aqua>";
    public String paySelf = "<red>You cannot pay yourself!</red>";
    @Comment("Use %tax_percentage% for tax percentage and %tax_applied% for tax applied to the transaction.")
    public String paySuccess = "<green>You paid %player% %amount%</green>";
    public String payFail = "<red>Payment failed!</red>";
    public String payReceived = "<green>You received %amount% from %player%!</green>";
    public String purgeUserSuccess = "<green>Users matching %player% have been purged!</green>";
    public String purgeBalanceSuccess = "<green>Users matching %player% have been reset for currency %currency%!</green>";
    public String switchCurrencySuccess = "<green>Switched %currency% to %switch-currency%.<br>Please restart immediately every instance<br> with RedisEconomy installed to avoid any overwrite!</green>";
    public String noTransactionFound = "<red>No transaction found for %player%!</red>";
    public String incorrectDate = "<red>Incorrect Date formatting !</red>";
    public String transactionsStart = "<dark_aqua>Transactions of player %player% from %after% to %before%!</dark_aqua>";
    public String transactionsEnd = "<dark_aqua>End of %player% transactions in %time% ms</dark_aqua>";
    public String transactionsArchiveCompleted = "<green>Archived %size% transaction accounts to %file%</green>";
    public String transactionsArchiveProgress = "<aqua>Archiving progress: %progress%% </aqua>";
    public String editMessageError = "<red>This config entry is not a String or doesn't exist!";
    public String editMessageClickHere = "<click:open_url:%url%>Click here to edit the message %field%!</click>";
    public String editMessageSuccess = "<green>Saved successfully %field%!";
    public TransactionItem transactionItem = new TransactionItem(
            "<dark_aqua>#%id%</dark_aqua> <click:copy_to_clipboard:%timestamp%><hover:show_text:\"<blue>Click to copy:</blue><br><aqua>%timestamp%</aqua>\"><gold>[Timestamp⌛]</hover></click> <click:run_command:/transaction %account-owner% %id% revert><hover:show_text:\"Click to revert transaction\"><red>[←Revert]</hover></click><br>" +
                    "<click:run_command:/transaction %account-owner% %afterbefore%><dark_green>%account-owner%</dark_green></click> <grey>> <white>%amount%%symbol% <grey>></grey> <click:run_command:/transaction %other-account% %afterbefore%><green>%other-account%</green></click><br>" +
                    "<yellow>Reason: </yellow>%reason%",
            "<dark_aqua>#%id%</dark_aqua> <click:copy_to_clipboard:%timestamp%><hover:show_text:\"<blue>Click to copy:</blue><br><aqua>%timestamp%</aqua>\"><gold>[Timestamp⌛]</hover></click> <click:run_command:/transaction %account-owner% %id% revert><hover:show_text:\"Click to revert transaction\"><red>[←Revert]</hover></click><br>" +
                    "<click:run_command:/transaction %other-account% %afterbefore%><green>%other-account%</green></click> <grey>> <white>%amount%%symbol% <grey>></grey> <click:run_command:/transaction %account-owner% %afterbefore%><dark_green>%account-owner%</dark_green></click><br>" +
                    "<yellow>Reason: </yellow>%reason%");
    public UnitSymbols unitSymbols = new UnitSymbols("k", "m", "b", "t", "q");
    public String backupRestoreFinished = "<green>Backup/restore file %file% finished!</green>";
    public String invalidPath = "<red>Invalid path!</red>";


    public record TransactionItem(
            String outgoingFunds,
            String incomingFunds
    ) {
    }

    public record UnitSymbols(
            String thousand,
            String million,
            String billion,
            String trillion,
            String quadrillion
    ) {
    }

    public NavigableMap<String, Long> getSuffixes() {
        return new TreeMap<>() {{
            put(unitSymbols.thousand(), 1_000L);
            put(unitSymbols.million(), 1_000_000L);
            put(unitSymbols.billion(), 1_000_000_000L);
            put(unitSymbols.trillion(), 1_000_000_000_000L);
            put(unitSymbols.quadrillion(), 1_000_000_000_000_000L);
        }};

    }

    public void send(CommandSender sender, String text) {
        audiences.sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(text));
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
