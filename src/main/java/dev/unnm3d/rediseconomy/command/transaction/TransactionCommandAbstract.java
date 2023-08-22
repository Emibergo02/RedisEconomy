package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import lombok.AllArgsConstructor;
import org.bukkit.command.CommandSender;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@AllArgsConstructor
public abstract class TransactionCommandAbstract {

    protected final RedisEconomyPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    void sendTransaction(CommandSender sender, int transactionId, Transaction transaction, String timestampArgument) {
        String accountOwnerName = transaction.accountIdentifier.isPlayer() ?//If the sender is a player
                plugin.getCurrenciesManager().getUsernameFromUUIDCache(transaction.accountIdentifier.getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.accountIdentifier.toString(); //Else, it's a bank, so we get the bank id
        String otherAccount = transaction.receiver.isPlayer() ?
                plugin.getCurrenciesManager().getUsernameFromUUIDCache(transaction.receiver.getUUID()) :
                transaction.receiver.toString();
        Currency currency = plugin.getCurrenciesManager().getCurrencyByName(transaction.currencyName);

        String transactionMessage = plugin.langs().transactionItem.getIncomingFunds();
        if (transaction.amount < 0) {
            transactionMessage = plugin.langs().transactionItem.getOutgoingFunds();
        }
        transactionMessage = transactionMessage
                .replace("%id%", String.valueOf(transactionId))
                .replace("%amount%", String.valueOf(transaction.amount))
                .replace("%symbol%", currency == null ? "" : currency.getCurrencyPlural())
                .replace("%account-owner%", accountOwnerName == null ? "Unknown" : accountOwnerName)
                .replace("%other-account%", otherAccount == null ? "Unknown" : otherAccount)
                .replace("%timestamp%", convertTimeWithLocalTimeZome(transaction.timestamp))
                .replace("%reason%", transaction.reason);
        if (timestampArgument != null)
            transactionMessage = transactionMessage.replace("%afterbefore%", timestampArgument);
        plugin.langs().send(sender, transactionMessage);
    }

    void sendTransaction(CommandSender sender, int transactionId, Transaction transaction) {
        sendTransaction(sender, transactionId, transaction, null);
    }

    String convertTimeWithLocalTimeZome(long time) {
        Date date = new Date(time);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }

    Date formatDate(String fromString) throws ParseException {
        return dateFormat.parse(fromString);
    }
}
